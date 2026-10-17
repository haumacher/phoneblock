#!/usr/bin/env bash
#
# Build, package, and upload a PhoneBlock-Dongle firmware release.
#
#   ./scripts/release.sh             # full flow: build, stage, upload, flip channel(s)
#   ./scripts/release.sh --stage     # build + stage to release/<version>/, no upload
#   ./scripts/release.sh --dry-run   # all of the above + print scp/ssh commands
#
# Version comes from `git describe --tags --match "dongle-v*"`. The working tree
# must be clean and HEAD must sit exactly on a tag — between-tag builds (e.g.
# 1.4.2-3-gabcdef) are rejected to keep the CDN to released versions only.
#
# The target channel is derived from the tag: a pre-release suffix
# (dongle-v1.6.0-rc1) publishes to the "beta" channel only; a clean
# release (dongle-v1.6.0) publishes to "stable" *and* "beta". That
# keeps beta always >= stable, so a beta tester lands on the final
# build once it ships and never sees a downgrade.

set -euo pipefail

FIRMWARE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="${FIRMWARE_DIR}/scripts"
BUILD_DIR="${FIRMWARE_DIR}/build"
# Further targets besides the classic ESP32, by IDF target name. The ESP32
# stays special (build/, top-level files, builds[0] and the top-level
# integrity block of manifest.json — what installed dongles read); every
# target listed here gets the same treatment:
#   - its own build dir build-<target>/ and own sdkconfig, so it never touches
#     the ESP32 build's configuration; target-specific defaults come from
#     sdkconfig.defaults.<target>,
#   - its artifacts (bins + ELF) under <version>/<target>/,
#   - a build entry in install.json.tmpl (the browser installer's manifest)
#     with the placeholders @<P>_APP_SHA256@ / @<P>_SIGNATURE@,
#     <P> = target_prefix (C3, S3, ...),
#   - its per-chip OTA file <channel>/ota-<target>.json.
# Adding a chip: append it here, extend chip_family(), add its build entry
# to install.json.tmpl. Not to manifest.json.tmpl: that file is frozen at
# ESP32 + ESP32-C3 for dongles that predate the per-chip OTA files and read
# it into 2047 bytes (checked below); the S3 entry alone would overflow it.
EXTRA_TARGETS=(esp32c3 esp32s3)
RELEASE_ROOT="${FIRMWARE_DIR}/release"

# Local settings (gitignored): KEEPASS_DB and friends so the user
# doesn't have to remember to `export` them before each release. The
# template lives at release.settings.template; copy and fill in.
# Sourced with `set -a` so plain KEY=VALUE lines are exported to
# subprocesses (sign-manifest.sh).
SETTINGS_FILE="${SCRIPT_DIR}/release.settings"
if [[ -f "$SETTINGS_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$SETTINGS_FILE"
    set +a
fi

CDN_HOST="haumac@cdn.phoneblock.net"
CDN_BASE="/public_html/cdn/dongle"
CDN_FIRMWARE="${CDN_BASE}/firmware"

MODE="full"
case "${1:-}" in
    --stage)   MODE="stage"   ;;
    --dry-run) MODE="dry-run" ;;
    "")        MODE="full"    ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
esac

# chipFamily as esp-web-tools spells it (main/chip_family.h: PB_CHIP_FAMILY).
chip_family() {
    case "$1" in
        esp32)   echo ESP32    ;;
        esp32c3) echo ESP32-C3 ;;
        esp32s3) echo ESP32-S3 ;;
        *) echo "ERROR: no chipFamily known for target $1" >&2; return 1 ;;
    esac
}

# Template placeholder prefix: esp32c3 -> C3 (@C3_APP_SHA256@, @C3_SIGNATURE@).
target_prefix() {
    local p="${1#esp32}"
    echo "${p^^}"
}

target_build_dir() {
    echo "${FIRMWARE_DIR}/build-$1"
}

# Fail early on a target without a chipFamily, before anything is built.
for T in "${EXTRA_TARGETS[@]}"; do
    chip_family "$T" > /dev/null
done

run() {
    if [[ "$MODE" == "dry-run" ]]; then
        printf '+ %s\n' "$*"
    else
        "$@"
    fi
}

# Run an sftp batch script (heredoc on stdin). In dry-run, just print it.
sftp_batch() {
    local script
    script="$(cat)"
    if [[ "$MODE" == "dry-run" ]]; then
        printf '+ sftp -b - %s <<EOF\n%s\nEOF\n' "$CDN_HOST" "$script"
    else
        printf '%s\n' "$script" | sftp -b - "$CDN_HOST"
    fi
}

# ---------------------------------------------------------------------------
# 1. Resolve version from git tag.
# ---------------------------------------------------------------------------
cd "$FIRMWARE_DIR"

if ! git diff --quiet HEAD -- . || ! git diff --cached --quiet HEAD -- .; then
    echo "ERROR: firmware tree has uncommitted changes; refusing to release." >&2
    exit 1
fi

DESCRIBE="$(git describe --tags --match 'dongle-v*' --exact-match 2>/dev/null || true)"
if [[ -z "$DESCRIBE" ]]; then
    echo "ERROR: HEAD is not a 'dongle-v*' tag. Tag the release first:" >&2
    echo "       git tag dongle-vX.Y.Z && git push --tags" >&2
    exit 1
fi
VERSION="${DESCRIBE#dongle-v}"
echo "Releasing version: $VERSION"

# Every release must ship a release-notes file. The web UI links the
# installed version to the suffix-stripped base
#   .../phoneblock-dongle/firmware/release-notes/<X.Y.Z>.md
# so a pre-release (1.3.4-rc1) points at the upcoming release's notes —
# which therefore must already exist when the rc is cut.
BASE_VERSION="${VERSION%%-*}"
NOTES="${FIRMWARE_DIR}/release-notes/${BASE_VERSION}.md"
if [[ ! -f "$NOTES" ]]; then
    echo "ERROR: missing release notes: release-notes/${BASE_VERSION}.md" >&2
    echo "       The web UI links the version there; create it before releasing." >&2
    exit 1
fi

# Derive the target channel(s) from the tag. A pre-release suffix
# (anything after a '-', e.g. 1.6.0-rc1) ships to beta only; a clean
# release ships to stable *and* beta so beta never lags behind stable.
# The dongle's semver compare treats "1.6.0-rc2" < "1.6.0", so a beta
# device upgrades onto the final build automatically once it lands.
if [[ "$VERSION" == *-* ]]; then
    CHANNELS=(beta)
    echo "Pre-release tag → channel: beta"
else
    CHANNELS=(stable beta)
    echo "Release tag → channels: stable + beta"
fi

# ---------------------------------------------------------------------------
# 2. Build firmware. ESP-IDF reads version from version.txt next to CMakeLists,
#    which lands in esp_app_desc_t.version and the /api/status payload.
# ---------------------------------------------------------------------------
# Move local build state out of the way so the release is built from
# sdkconfig.defaults alone — gitignored sticky config has no business
# in a CDN artifact:
#   * sdkconfig                  — frozen from the last dev build,
#                                  may keep CONFIG_SIP_TEST_FORCE_… or
#                                  similar dev knobs after the .local
#                                  was already cleaned up.
#   * sdkconfig.defaults.local   — typically points at the test
#                                  PhoneBlock backend, ships WiFi
#                                  credentials, enables test hooks.
# Files are restored on EXIT regardless of how the script ends, so a
# dev workspace is undisturbed even if the build fails or the user
# Ctrl-C's. Trap is installed *before* the stash so a Ctrl-C between
# the two mv's still restores cleanly.
STASHED=()
cleanup() {
    rm -f "${FIRMWARE_DIR}/version.txt"
    for f in "${STASHED[@]}"; do
        if [[ -e "${f}.release-stash" ]]; then
            mv "${f}.release-stash" "$f"
        fi
    done
}
trap cleanup EXIT

# Only now, with cleanup armed, write the file — it must not outlive this
# script. Written before the trap it could be orphaned by any early exit in
# between, including the leftover-stash check just below, and a stale
# version.txt then silently overrides `git describe` for every later dev build
# in this workspace (that is how a 1.4.1 file came to stamp 1.5.x builds).
echo "$VERSION" > "${FIRMWARE_DIR}/version.txt"

for f in "${FIRMWARE_DIR}/sdkconfig" "${FIRMWARE_DIR}/sdkconfig.defaults.local"; do
    # Refuse to run if a previous release.sh died before its trap got
    # to restore — otherwise the mv below would clobber the original.
    if [[ -e "${f}.release-stash" ]]; then
        echo "ERROR: leftover ${f}.release-stash from a previous run." >&2
        echo "       Inspect it, then either 'mv ${f}.release-stash ${f}'" >&2
        echo "       or 'rm ${f}.release-stash' before retrying." >&2
        exit 1
    fi
    if [[ -e "$f" ]]; then
        mv "$f" "${f}.release-stash"
        STASHED+=("$f")
    fi
done

if [[ -z "${IDF_PATH:-}" ]]; then
    echo "ERROR: IDF_PATH not set. Source ESP-IDF's export.sh first." >&2
    exit 1
fi

# Wipe the build directory so the release is configured from
# sdkconfig.defaults from scratch. Stashing the gitignored `sdkconfig`
# above is NOT enough on its own: `idf.py build` against an existing
# build/ reuses the cached build/config/sdkconfig.h and does not re-run
# Kconfig just because `sdkconfig` vanished. That is how 1.3.4 shipped
# with CONFIG_ESP_TASK_WDT_TIMEOUT_S=5 even though sdkconfig.defaults
# had already raised it to 120 — the cached 5 s config from the last dev
# build was baked into the CDN artifact. A full wipe forces regeneration
# from defaults, so every config value in sdkconfig.defaults actually
# reaches the released binary.
rm -rf "$BUILD_DIR"
for T in "${EXTRA_TARGETS[@]}"; do
    rm -rf "$(target_build_dir "$T")"
done

idf.py -C "$FIRMWARE_DIR" build
for T in "${EXTRA_TARGETS[@]}"; do
    TB="$(target_build_dir "$T")"
    idf.py -C "$FIRMWARE_DIR" -B "$TB" \
        -DIDF_TARGET="$T" -DSDKCONFIG="${TB}/sdkconfig" build
done

# ---------------------------------------------------------------------------
# 3. Stage release artifacts into release/<version>/.
# ---------------------------------------------------------------------------
STAGE_VERSION="${RELEASE_ROOT}/${VERSION}"
# Channel manifest: absolute URLs into the version dir. Identical for
# every channel (stable/beta), so it's built once and uploaded to each
# target channel in step 4.
STAGE_CHAN="${RELEASE_ROOT}/channel"
rm -rf "$STAGE_VERSION" "$STAGE_CHAN"
mkdir -p "$STAGE_VERSION" "$STAGE_CHAN"

cp "${BUILD_DIR}/bootloader/bootloader.bin"           "${STAGE_VERSION}/"
cp "${BUILD_DIR}/partition_table/partition-table.bin" "${STAGE_VERSION}/"
cp "${BUILD_DIR}/ota_data_initial.bin"                "${STAGE_VERSION}/"
cp "${BUILD_DIR}/phoneblock_dongle.bin"               "${STAGE_VERSION}/"

# Artifacts of the further targets go into a <target>/ subdirectory with the
# same file names: the ESP32 files keep their established URLs (installed
# dongles and the crash-analysis tooling know them), and the OTA path finds
# the app by its "/phoneblock_dongle.bin" suffix either way.
for T in "${EXTRA_TARGETS[@]}"; do
    TB="$(target_build_dir "$T")"
    mkdir -p "${STAGE_VERSION}/${T}"
    cp "${TB}/bootloader/bootloader.bin"           "${STAGE_VERSION}/${T}/"
    cp "${TB}/partition_table/partition-table.bin" "${STAGE_VERSION}/${T}/"
    cp "${TB}/ota_data_initial.bin"                "${STAGE_VERSION}/${T}/"
    cp "${TB}/phoneblock_dongle.bin"               "${STAGE_VERSION}/${T}/"
done

# Ship the unstripped ELF alongside the .bin. Not used by devices —
# it is the symbolication input for crash dumps that the dongle
# uploads to /api/dongle/coredump. Resolve a backtrace later with:
#   espcoredump.py info_corefile -c <dump.elf> phoneblock_dongle.elf
# No signing, no manifest entry: this is a debug artefact, not part
# of the OTA payload.
cp "${BUILD_DIR}/phoneblock_dongle.elf"               "${STAGE_VERSION}/"
for T in "${EXTRA_TARGETS[@]}"; do
    cp "$(target_build_dir "$T")/phoneblock_dongle.elf" "${STAGE_VERSION}/${T}/"
done

# Sign the app binaries. sign-manifest.sh pulls the private key from
# KeePassXC (one master password prompt on tty for all of them), signs a
# deterministic payload of (domain-tag, version, app-sha256) per binary, and
# emits two KEY=VALUE lines per binary in argument order: the i-th SHA256=
# and the i-th SIG= line belong to SIGN_BINS[i]. The ESP32 signature is the
# manifest's top-level integrity block (what every dongle before C3 support
# reads); every other one sits inside its own build entry.
SIGN_BINS=("${STAGE_VERSION}/phoneblock_dongle.bin")
for T in "${EXTRA_TARGETS[@]}"; do
    SIGN_BINS+=("${STAGE_VERSION}/${T}/phoneblock_dongle.bin")
done
SIGN_OUT="$("${SCRIPT_DIR}/sign-manifest.sh" "${VERSION}" "${SIGN_BINS[@]}")"
mapfile -t SIGN_SHAS < <(printf '%s\n' "$SIGN_OUT" | sed -n 's/^SHA256=//p')
mapfile -t SIGN_SIGS < <(printf '%s\n' "$SIGN_OUT" | sed -n 's/^SIG=//p')
if (( ${#SIGN_SHAS[@]} != ${#SIGN_BINS[@]} || ${#SIGN_SIGS[@]} != ${#SIGN_BINS[@]} )); then
    echo "ERROR: sign-manifest.sh returned ${#SIGN_SHAS[@]} SHA256 and ${#SIGN_SIGS[@]} SIG" >&2
    echo "       lines for ${#SIGN_BINS[@]} binaries." >&2
    exit 1
fi
for i in "${!SIGN_BINS[@]}"; do
    # Cross-check the pairing: the i-th hash must be the i-th binary's.
    if [[ ! "${SIGN_SHAS[$i]}" =~ ^[0-9a-f]{64}$ || -z "${SIGN_SIGS[$i]}" \
          || "${SIGN_SHAS[$i]}" != "$(sha256sum "${SIGN_BINS[$i]}" | awk '{print $1}')" ]]; then
        echo "ERROR: sign-manifest.sh output does not match ${SIGN_BINS[$i]}." >&2
        exit 1
    fi
done
APP_SHA256="${SIGN_SHAS[0]}"
APP_SIG="${SIGN_SIGS[0]}"
declare -A TARGET_SHA256 TARGET_SIG
for i in "${!EXTRA_TARGETS[@]}"; do
    TARGET_SHA256["${EXTRA_TARGETS[$i]}"]="${SIGN_SHAS[$((i + 1))]}"
    TARGET_SIG["${EXTRA_TARGETS[$i]}"]="${SIGN_SIGS[$((i + 1))]}"
done

# Placeholder substitutions for both templates. `|` as sed-delimiter for
# the signature lines because the base64 output may contain `/` (no need for
# an explicit escape, and `+`/`=` are already safe).
MANIFEST_SED=(-e "s/@VERSION@/${VERSION}/g"
              -e "s/@APP_SHA256@/${APP_SHA256}/g"
              -e "s|@SIGNATURE@|${APP_SIG}|g")
for T in "${EXTRA_TARGETS[@]}"; do
    P="$(target_prefix "$T")"
    MANIFEST_SED+=(-e "s/@${P}_APP_SHA256@/${TARGET_SHA256[$T]}/g"
                   -e "s|@${P}_SIGNATURE@|${TARGET_SIG[$T]}|g")
done

# Two manifests in esp-web-tools format, each rendered twice:
#   manifest.json — legacy, ESP32 (builds[0] + top-level integrity) and
#                   ESP32-C3 only. Dongles before the per-chip OTA files poll
#                   <channel>/manifest.json; must stay as it is (and small).
#   install.json  — every chip, for the browser installer (install page).
# Versioned copies (relative paths) are served from /dongle/firmware/<version>/;
# the channel copies carry absolute URLs into the version directory, so the
# pinned <channel>/... URLs never change and only these files flip per
# channel at release time.
BASE_URL="https://cdn.phoneblock.net/dongle/firmware/${VERSION}"
for M in manifest install; do
    sed "${MANIFEST_SED[@]}" "${SCRIPT_DIR}/${M}.json.tmpl" \
        > "${STAGE_VERSION}/${M}.json"
    sed "${MANIFEST_SED[@]}" \
        -e "s|\"path\": \"|\"path\": \"${BASE_URL}/|g" \
        "${SCRIPT_DIR}/${M}.json.tmpl" \
        > "${STAGE_CHAN}/${M}.json"
    # No placeholder left unsubstituted (template entry for a target that
    # is missing from EXTRA_TARGETS).
    if grep -q '@[A-Z0-9_]*@' "${STAGE_VERSION}/${M}.json"; then
        echo "ERROR: unsubstituted placeholder in ${M}.json:" >&2
        grep -o '@[A-Z0-9_]*@' "${STAGE_VERSION}/${M}.json" >&2
        exit 1
    fi
done

# The installer manifest must offer every released chip: no target added to
# EXTRA_TARGETS but forgotten in install.json.tmpl.
for T in "${EXTRA_TARGETS[@]}"; do
    if ! grep -qF "\"chipFamily\": \"$(chip_family "$T")\"" "${STAGE_VERSION}/install.json"; then
        echo "ERROR: install.json.tmpl has no build for $(chip_family "$T") ($T)." >&2
        exit 1
    fi
done

# Dongles from before the per-chip OTA files (and newer ones on a channel
# without them) read the channel manifest into a 2048-byte buffer
# (firmware_update.c) and treat a longer one as a failed fetch — they would
# silently stop updating. Refuse to publish anything that doesn't fit.
MANIFEST_SIZE="$(wc -c < "${STAGE_CHAN}/manifest.json")"
if (( MANIFEST_SIZE > 2047 )); then
    echo "ERROR: channel manifest is ${MANIFEST_SIZE} bytes; installed dongles" >&2
    echo "       only read 2047. Shorten scripts/manifest.json.tmpl (new chips" >&2
    echo "       belong in install.json.tmpl only)." >&2
    exit 1
fi

# Per-chip OTA files: what the dongles actually poll (firmware_update.c).
# One build each, so adding a chip never grows a file installed dongles read.
# The file name carries the IDF target name (CONFIG_IDF_TARGET).
"${SCRIPT_DIR}/ota-manifest.sh" "$VERSION" ESP32 \
    "${BASE_URL}/phoneblock_dongle.bin" "$APP_SHA256" "$APP_SIG" \
    > "${STAGE_CHAN}/ota-esp32.json"
for T in "${EXTRA_TARGETS[@]}"; do
    "${SCRIPT_DIR}/ota-manifest.sh" "$VERSION" "$(chip_family "$T")" \
        "${BASE_URL}/${T}/phoneblock_dongle.bin" \
        "${TARGET_SHA256[$T]}" "${TARGET_SIG[$T]}" \
        > "${STAGE_CHAN}/ota-${T}.json"
done

# Plain pointer file for tooling that wants just the version string.
printf '{ "version": "%s" }\n' "$VERSION" > "${STAGE_CHAN}/version.json"

echo "Staged: $STAGE_VERSION"
echo "Staged: $STAGE_CHAN → channels: ${CHANNELS[*]}"

if [[ "$MODE" == "stage" ]]; then
    exit 0
fi

# ---------------------------------------------------------------------------
# 4. Upload version directory, then atomically flip each target channel.
# ---------------------------------------------------------------------------
REMOTE_VERSION="${CDN_FIRMWARE}/${VERSION}"

# The CDN host accepts sftp/scp only — no shell access. Parent dirs are
# created via sftp (-mkdir ignores "already exists"). The version dir is
# explicitly mkdir'd so the subsequent scp always sees an existing target —
# that avoids the `scp -r DIR DEST`-footgun where the result depends on
# whether DEST exists (creates DEST.flat the first time, DEST/DIR/... the
# second time).
TARGET_MKDIRS="$(for T in "${EXTRA_TARGETS[@]}"; do
    printf -- '-mkdir %s\n' "${REMOTE_VERSION}/${T}"
done)"
sftp_batch <<SFTP
-mkdir ${CDN_BASE}
-mkdir ${CDN_FIRMWARE}
-mkdir ${REMOTE_VERSION}
${TARGET_MKDIRS}
SFTP

# Plain files only, one scp per directory, no -r: OpenSSH >= 9 runs scp over
# the SFTP protocol, and a recursive upload into a directory that doesn't
# exist yet fails there with "path canonicalization failed" — so every
# remote directory is created above and scp only ever copies files into it.
VERSION_FILES=()
for f in "${STAGE_VERSION}"/*; do
    [[ -f "$f" ]] && VERSION_FILES+=("$f")
done
run scp "${VERSION_FILES[@]}" "${CDN_HOST}:${REMOTE_VERSION}/"
for T in "${EXTRA_TARGETS[@]}"; do
    run scp "${STAGE_VERSION}/${T}"/* "${CDN_HOST}:${REMOTE_VERSION}/${T}/"
done

# ---- localized i18n assets, co-located under this version dir -------------
# One release, one location: the committed announcement recordings + mail/UI
# packs are published to firmware/<version>/i18n/, so the device fetches them
# from the same version tree as its .bin (see i18n_sync.c). This is the single
# deploy step for #460 — no key and no extra services required (the i18n
# manifest is unsigned, unlike the OTA one; recordings and reviewed text packs
# are committed in i18n/). If it fails, the firmware release still completes — publish
# i18n separately with scripts/i18n-assets.sh --version ${VERSION}.
# i18n is published under the release tag (VERSION), so each release — incl.
# each rc — gets its own bundle. Dev/test builds strip only their git-describe
# suffix and resolve their tag's bundle (see version_release_tag / i18n_sync.c).
I18N_ARGS=(--version "${VERSION}")
[[ "$MODE" == "dry-run" ]] && I18N_ARGS+=(--dry-run)
if ! "${SCRIPT_DIR}/i18n-assets.sh" "${I18N_ARGS[@]}"; then
    echo "WARNING: i18n asset publish failed for ${VERSION}. The firmware is" >&2
    echo "         released; run scripts/i18n-assets.sh --version ${VERSION}" >&2
    echo "         once the CDN is reachable again." >&2
fi

# Atomic flip per channel: upload to *.tmp, then rename over the live file.
# OpenSSH's sftp uses posix-rename, which atomically replaces the target. The
# window in which <channel>/manifest.json doesn't exist is zero (after the
# first release) or one upload (the very first release of that channel, where
# there's nothing to overwrite yet).
# All puts first, then all renames, version.json last.
CHANNEL_FILES=(manifest.json install.json ota-esp32.json)
for T in "${EXTRA_TARGETS[@]}"; do
    CHANNEL_FILES+=("ota-${T}.json")
done
CHANNEL_FILES+=(version.json)
for CH in "${CHANNELS[@]}"; do
    REMOTE_CH="${CDN_FIRMWARE}/${CH}"
    PUTS="$(for F in "${CHANNEL_FILES[@]}"; do
        printf 'put %s %s\n' "${STAGE_CHAN}/${F}" "${REMOTE_CH}/${F}.tmp"
    done)"
    RENAMES="$(for F in "${CHANNEL_FILES[@]}"; do
        printf 'rename %s %s\n' "${REMOTE_CH}/${F}.tmp" "${REMOTE_CH}/${F}"
    done)"
    sftp_batch <<SFTP
-mkdir ${REMOTE_CH}
${PUTS}
${RENAMES}
SFTP
done

echo
echo "Released ${VERSION}."
echo "  Pinned: https://cdn.phoneblock.net/dongle/firmware/${VERSION}/install.json"
for CH in "${CHANNELS[@]}"; do
    echo "  ${CH}: https://cdn.phoneblock.net/dongle/firmware/${CH}/install.json"
done

# Push the tag now that the CDN has accepted the upload. Pushing
# only after a successful release means a failed run (build error,
# signing failure, network drop during upload) leaves the tag local,
# so the user can `git tag -d ${DESCRIBE}` and re-tag the same
# version without a force-push to the remote.
run git push origin "$DESCRIBE"
