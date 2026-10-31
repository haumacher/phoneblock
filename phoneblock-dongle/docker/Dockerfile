# The dongle's Linux service as a container image, built from the released
# Debian package in the APT repository (https://haumacher.github.io/phoneblock/apt/),
# so the image runs exactly the package a Raspberry Pi installs. The workflow
# .github/workflows/dongle-linux.yml builds it after each release and pushes
# it to Docker Hub; debian/README.md describes how to run it.
#
# Local build, from this directory (Debian version syntax, see debian/pb-version):
#   docker build --build-arg PHONEBLOCK_VERSION=1.7.0~beta7 -t phoneblock-dongle .

# The Debian release must match the package's suite: each suite's package is
# built against that release's libraries.
ARG PHONEBLOCK_SUITE=trixie

FROM debian:${PHONEBLOCK_SUITE}-slim

ARG PHONEBLOCK_SUITE
# Fingerprint of the repository's signing key, checked before it is trusted.
ARG PHONEBLOCK_REPO_FINGERPRINT=0A1948B95F45BADD626CB753ADFAC8AA5581163F

# A failing step in a pipe (gpg | grep) fails the build.
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# Changes the base layer below; dongle-linux.yml passes the ISO week, so the
# base is rebuilt from a fresh, upgraded Debian once a week at most.
ARG PHONEBLOCK_BASE_EPOCH=none
# The package's libraries (its Depends, as names of this suite), installed in
# the base layer. A dependency missing here fails the release layer below.
ARG PHONEBLOCK_DEPENDENCIES="adduser libcjson1 libcurl4t64 libmicrohttpd12t64 libsrtp2-1 libssl3t64"

# Base layer, the same for every release: it uses no version-dependent
# argument, so the build cache (cache-from in dongle-linux.yml) reuses it.
# Under QEMU this saves almost the whole arm build: apt reading the Debian
# package index, ca-certificates, gpg.
#
# The service user gets fixed ids before the package's postinst would pick
# the next free ones, so files in an existing volume keep matching it across
# image versions. curl stays for the health check; tzdata makes
# TZ=Europe/Berlin work.
RUN echo "Base ${PHONEBLOCK_BASE_EPOCH}" \
    && groupadd --system --gid 101 phoneblock \
    && useradd --system --uid 100 --gid phoneblock --home-dir /var/lib/phoneblock \
         --no-create-home --shell /usr/sbin/nologin phoneblock \
    && apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends ca-certificates curl gpg tzdata \
         ${PHONEBLOCK_DEPENDENCIES} \
    && curl -fsSLo /usr/share/keyrings/phoneblock-dongle.gpg \
         https://haumacher.github.io/phoneblock/apt/phoneblock-dongle.gpg \
    && gpg --show-keys --with-colons /usr/share/keyrings/phoneblock-dongle.gpg \
         | grep -q "^fpr:::::::::${PHONEBLOCK_REPO_FINGERPRINT}:$" \
    && apt-get purge -y gpg \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

ARG PHONEBLOCK_VERSION
ARG PHONEBLOCK_CHANNEL=beta

LABEL org.opencontainers.image.title="PhoneBlock Dongle" \
      org.opencontainers.image.description="PhoneBlock SIP spam-call blocker for the Fritz!Box (Linux version of the dongle firmware)" \
      org.opencontainers.image.source="https://github.com/haumacher/phoneblock" \
      org.opencontainers.image.url="https://phoneblock.net/phoneblock/dongle" \
      org.opencontainers.image.licenses="GPL-3.0-or-later" \
      org.opencontainers.image.version="${PHONEBLOCK_VERSION}"

# Release layer: only the package. apt reads only the PhoneBlock repository,
# its libraries are in the base layer.
RUN test -n "${PHONEBLOCK_VERSION}" \
        || { echo "Set --build-arg PHONEBLOCK_VERSION (e.g. 1.7.0~beta7)" >&2; exit 1; } \
    && echo "deb [signed-by=/usr/share/keyrings/phoneblock-dongle.gpg] https://haumacher.github.io/phoneblock/apt ${PHONEBLOCK_SUITE} ${PHONEBLOCK_CHANNEL}" \
         > /etc/apt/sources.list.d/phoneblock-dongle.list \
    && apt-get update -o Dir::Etc::SourceList=/etc/apt/sources.list.d/phoneblock-dongle.list \
         -o Dir::Etc::SourceParts=- \
    && apt-get install -y --no-install-recommends \
         "phoneblock-dongle=${PHONEBLOCK_VERSION}" \
    && rm -rf /var/lib/apt/lists/*

COPY entrypoint.sh /usr/local/bin/phoneblock-dongle-entrypoint

# The platform comment in the User-Agent, so PhoneBlock's API key list tells
# container installs from the Debian package ("Linux").
ENV PHONEBLOCK_PLATFORM="Linux; Docker"

# Settings, device id and the announcement.
VOLUME /var/lib/phoneblock
# Web UI. SIP and RTP use UDP ports from the settings (sip_lport, rtp_port);
# run the container with host networking, see debian/README.md.
EXPOSE 8080

# Asks the address and port the web UI listens on: set them with
# PHONEBLOCK_WEB_BIND and PHONEBLOCK_WEB_PORT, not with --web/--web-bind.
HEALTHCHECK --interval=60s --timeout=5s --start-period=30s \
    CMD host="${PHONEBLOCK_WEB_BIND:-127.0.0.1}"; \
        case "$host" in 0.0.0.0) host=127.0.0.1 ;; ::) host="[::1]" ;; *:*) host="[$host]" ;; esac; \
        curl -fsS "http://$host:${PHONEBLOCK_WEB_PORT:-8080}/health" >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/phoneblock-dongle-entrypoint"]
