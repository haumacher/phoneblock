---
name: phoneblock-workflow
description: The working method for developing PhoneBlock — orchestrating sub-agents across the four toolchains (Maven server/answerbot, Flutter mobile and answerbot UI, ESP-IDF dongle firmware with its host test suite, the DeepL translation pipelines), adversarial probe reviews, the per-toolchain verification gate, the run loops (local Jetty, pb-test, the dongle rig at http://answerbot), GitHub-issue triage, the branch-and-PR discipline, and the format/compatibility doctrines. Load at the start of any session doing feature or bug work on this repository.
---

# The PhoneBlock working method

This skill is the meta-knowledge of how a session on this repository should run. It complements,
never duplicates, `CLAUDE.md` (build facts, conventions, the I18N and migration rules), the
module-level `phoneblock-dongle/firmware/CLAUDE.md` and `phoneblock_mobile/CLAUDE.md`, and the
sibling skills: `fix-issue` (start an issue on the right base branch), `cleanup-workspace` (park a
worktree), `dongle-crash-analysis` (decode a `.coredump`), `diag-tuning` (the server-log
diagnostics). Read the root `CLAUDE.md` first, then the module `CLAUDE.md` of the component you
touch. The GitHub issue tracker at `haumacher/phoneblock` **is** the work queue (§1, §8).
Design records live in `docs/plans/` (dated `*-design.md` / `*-plan.md`); a non-trivial feature gets
one before code.

## 1. The operating loop

Work is a pipeline, not a pile:

1. **Intake**: user reports (chat or GitHub issues) and `gh issue list -R haumacher/phoneblock`.
   Bugs outrank queued features; a `test-report` from a dongle user outranks both when it names a
   regression. Triage every new issue with a diagnosis comment *before* fixing, in the issue's
   language (most are German). Apply the existing labels: component (`server`, `dongle`,
   `pb-mobile`, `answerbot`, `docker`, `blocklist`), kind (`bug`, `enhancement`, `question`,
   `support`, `documentation`, `test-report`), state (`in-progress`, `feedback-required`),
   resolution (`cantfix`, `wontfix`, `duplicate`). A `support`/`question` issue is answered, not
   coded.
2. **Reproduce before dispatching.** A server report gets a JUnit test under `phoneblock/src/test`
   (the `db`, `app`, `carddav`, `dns`, `mail` test packages are the behavioral contract) or a
   `curl` against `https://phoneblock.net/pb-test/api/...` (the test deployment reads live data).
   A dongle report gets a host test in `phoneblock-dongle/firmware/test` (pure C, `make test`,
   runs in milliseconds) or a faked call on the rig (§7). A mobile report gets a `flutter test`
   with the HTTP client injected (widget tests cannot reach the network, §4). Know whether it is a
   real bug, an already-fixed one, or your own probe error before an agent burns tokens. If the
   main tree is agent-owned, reproduce in one of the parked worktrees (`pb-1`..`pb-3`, §10), never
   in the live tree.
3. **Branch first.** Use `fix-issue` for issue work: it checks the tree is clean, reads the issue
   and branches `issue-<nr>-<short-description>` off *the branch where the fix ships*. Non-issue
   work branches by topic (`dongle-<topic>`, `fix-<topic>`). Dongle fixes for a shipped release
   start on `dongle-X.Y.x` and are forward-merged to `master` — never the reverse (CLAUDE.md,
   *Dongle Release Branches*).
4. **Dispatch** one sub-agent per coherent package (§2). Sequential by default; parallel only with
   disjoint file ownership stated in both briefs (§3). The toolchains give a natural split.
5. **Review with a novel probe** (§4). Non-negotiable.
6. **Gate** (§5), **commit and open the PR** (§6), **merge it yourself** after the gate ran on
   the merged tree (the user does not merge), **rerun the component** (§7), then hand the user
   the merge commit, the PR link and what to try.
7. **Before ever finishing: `gh issue list -R haumacher/phoneblock`.** Anything you defer becomes
   an issue or an issue comment, so a crashed session loses nothing.

Track packages with the task list; keep statuses honest (in_progress on dispatch, completed only
after the PR is merged).

## 2. Agent briefs — what makes them work

Default to Opus for feature/bug packages; Sonnet only for genuinely mechanical chores
(translation re-runs, moving files). Run in background; you'll be notified. A brief that produces
a good delivery contains, in order:

- **Grounding**: "Read `CLAUDE.md` and `<component>/CLAUDE.md`. Study <the specific classes,
  the specific `.c`/`.h` pair, the specific host test or JUnit test that is the behavioral
  contract>." Name the module (`phoneblock`, `phoneblock-shared`, `phoneblock-ab`,
  `phoneblock-dongle/firmware`, `phoneblock_mobile`, `phoneblock_answerbot_ui`) and whether a
  `.proto` in `phoneblock-shared`, the DB schema, or the OpenAPI spec is in scope.
- **The task as design intent**, not implementation orders — with the load-bearing decisions
  made: what the server stores vs. what a client derives, what the API carries vs. what the
  dongle computes, what refuses with a visible reason vs. what heals silently. Quote the user's
  own words where they designed the feature; their design is usually better than your draft.
- **User fixtures embedded verbatim** as required regression tests: a dongle log-panel excerpt, a
  Fritz!Box configuration, a SIP trace, a JSON response — whatever the report contained, **with
  real phone numbers, emails and device UUIDs replaced by synthetic ones first** (§8).
- **Explicit acceptance tests**, including exact values where computable (a normalized number, a
  blocklist entry count, a SIP response line). "write → read back → equal" belongs in every brief
  that touches a persisted or wire format.
- **The build discipline block** (the point is not wasting runs, not avoiding them):
  - Maven: the edit loop runs the **owning module only**, output to a log file as CLAUDE.md
    shows (`mvn -B -pl phoneblock -DskipTests install >/tmp/pb-build.log 2>&1`, then `tail`
    and `grep '\[ERROR\]'`). A change in `phoneblock-shared` needs `-am` or a prior `install`;
    a `.proto` change needs `mvn -B -pl phoneblock-shared process-sources` **before** any
    consumer compiles (the msgbuf goal binds to `process-sources`, not `generate-sources`). Never wrap the build in `&& ... || { ... }` (it trips the permission
    allowlist). The full `mvn -B clean install` runs once at the end before the report.
  - Flutter: the edit loop runs `flutter analyze` and the **targeted** test file; the full
    `flutter analyze && flutter test` runs at the end, in the app that changed.
  - Dongle: the edit loop is `make test` in `firmware/test` (add the new test to `BINS` in the
    Makefile) plus `idf.py build`; the esp32c3 and esp32s3 builds
    (`idf.py -B build-esp32c3 -DIDF_TARGET=esp32c3 -DSDKCONFIG=build-esp32c3/sdkconfig build`,
    same with `esp32s3`) run at the end. `source ~/tools/esp/esp-idf/export.sh` first.
  - A run whose inputs did not change is not repeated "to be sure". Batch independent lookups.
- **The standing rules block**: ALL existing tests green in every toolchain the change touches;
  **never hand-edit generated code** (msgbuf Java classes, `app_*.arb` except `app_de.arb`,
  `app_localizations*.dart`, every non-`de/` template directory, every `Messages_<lang>.properties`
  except `_de`) — edit the German source and run the pipeline; **the dongle string-builder rule**
  (`strbuf.h`, no `n += snprintf` accumulation; `sprintf`/`strcat` are poisoned); **servlets never
  `resp.sendError()` on non-GET** (`ServletUtil.sendMessage`); **an API change updates
  `phoneblock/src/main/webapp/api/phoneblock.json`**; **a schema change is a numbered migration**;
  do NOT commit; **nothing half-done — cut whole items only, refuse in-app with a visible reason,
  and report every cut**.
- **Ask for a report**: mechanism chosen and why, alternatives rejected, cuts, test count
  before/after per toolchain, and what was run on hardware if anything. The report is your
  review input; a vague report predicts a vague delivery.

**Reworks go back to the same agent** via SendMessage with the failing probe path and the demand:
*fix it generally, not to the probe*. If several rework rounds fail, question the architecture,
not the tests.

**Stalled agents are real.** If a background agent's transcript goes quiet: arm a Monitor on the
output file's mtime (10-minute quiet threshold, until-loop, not tail -f). **The tasks-dir output
path is a symlink — stat it with `-L`**, or the monitor reads the symlink's own never-changing
mtime and cries wolf in exact 600 s increments. Verify before acting on any event: a quiet
transcript with a busy `mvn`, `idf.py` or `gradle` process is a long build, not a stall. On
silence: nudge-resume via SendMessage ("Resume exactly where you left off: <last visible
step>"). If the transcript stays frozen after a resume, the agent is dead — take the remainder
over yourself; its uncommitted work is usually further along than the last message suggests
(`git status`, then run its tests before redoing anything).

**Crash recovery.** The crashed session's transcript is
`~/.claude/projects/<project-dir>/<session-id>.jsonl` (one project dir per worktree, e.g.
`-home-haui-devel-phoneblock`, `-home-haui-devel-pb-1`); its sub-agents' transcripts are
`<session-id>/subagents/agent-*.jsonl` beside it; `~/.claude/tasks/<session-id>/` holds the task
list. Pull the last `Agent` tool_use out of the session transcript with a short python filter —
that is the brief, verbatim — and the last tool calls out of the newest sub-agent transcript to see
where it died. The agent's uncommitted work is in the tree: `git status`, the owning module's
build, then **re-dispatch the same brief with a resume preamble** stating exactly what exists,
what compiles, and what is not started; never redo the work. A local Jetty does **not** survive a
crash (`ss -ltnp | grep 8080`; restart per §7). Check `gh issue list` immediately — reports filed
during the crash are untriaged.

## 3. Concurrency — the rules

- **Never let two agents edit the same file.** When parallelizing, write an explicit FILE BOUNDARY
  paragraph into *both* briefs ("do not touch X, Y, Z; if you must, STOP and report").
- **Each generated-code source has one owner per round.** `phoneblock-shared/src/main/proto`
  generates the Java model consumed by server, answerbot and tools; `app_de.arb` generates every
  other ARB and the Dart localizations; the `de/` templates and `Messages_de.properties`
  generate every other locale; `firmware/i18n/l10n/*_de.arb` generates the dongle packs. Whoever
  changes the source owns the regeneration and the compile fallout on every consumer; the other
  agent must not touch that source or rebuild its module mid-edit.
- **Concurrent Maven runs in one tree collide** on `target/` and on `~/.m2` installs of the same
  SNAPSHOT. Never run `mvn` in a tree an agent is mid-edit in; give a second agent its own
  worktree (§10) and have it use `mvn verify` (not `install`) so it cannot overwrite the main
  tree's installed artifacts.
- **Concurrent `idf.py` builds share `build/`** and the `sdkconfig`; two dongle agents need two
  trees. The esp32c3 build already uses its own `build-esp32c3/` directory, so one agent may run
  both targets.
- **The rig is a single device.** Only one agent flashes or injects calls at `http://answerbot`
  at a time, and never while the user is reading its log panel (§7).
- A commit made while an agent has uncommitted work in the tree must be **selective**
  (`git add <files>`).

## 4. The probe review (the quality mechanism)

After a delivery reports done and before committing: **write a test the agent never saw**,
composing the new feature with pre-existing features — a wildcard block next to a whitelist
entry, a number in national format from a non-German country, a SIP message at the buffer
limit, a blocklist incremental sync across a version gap, a Fritz!Box with two phonebooks, a
page rendered in a locale other than `de`. Good probes ask "is the mechanism general?" not
"does the happy path work?". Keep passing probes as permanent tests (`Test<Feature>Probe` in
Java, `test_<feature>.c` in the firmware suite, `<feature>_probe_test.dart` in Flutter).

**Expect your own probes to be wrong first.** The recurring traps — check these before blaming the
delivery:

- **Flutter widget tests cannot reach the network**: under `TestWidgetsFlutterBinding` every
  HttpClient request returns 400 and nothing is sent. Inject a fake client or test the widget
  below the fetch. `phoneblock_answerbot_ui/test/widget_test.dart` is the Flutter starter
  template (a counter smoke test against an app that has no counter) — a failure there is a
  known project defect, not your regression; say so explicitly.
- **A stale module install**: a test in `phoneblock` that behaves as if a `phoneblock-shared`
  change never happened is running against the old `~/.m2` artifact — rebuild with `-am` or
  `install` the shared module. **`mvn -pl phoneblock-ab` alone does not build** (even on a
  clean master): `module-info.java` fails with `module not found: de.haumacher.msgbuf` when
  the shared module comes from `~/.m2` instead of the reactor. Always build the answerbot as
  `-pl phoneblock-shared,phoneblock-ab` or with `-am`.
- **`mvn install` of the `phoneblock` module needs the Flutter answerbot UI built first**
  (`cd phoneblock_answerbot_ui && flutter build web --base-href /phoneblock/ab/`); without
  `build/web` the WAR step fails after all tests passed — that is the environment, not the
  change. Generated msgbuf classes drift by design after a `.proto` edit;
  a compile error there is the build telling you to regenerate, not a defect.
- **`MissingResourceException` at runtime** after adding a key to `Messages_de.properties`
  means the other bundles were not regenerated: the DeepL step only runs under the `with-deepl`
  profile (CLAUDE.md shows the explicit `tl-maven-plugin:translate` invocation). A new page
  without its `page.<name>.title` key fails to render for the same reason.
- **A new ARB key without `./gradlew translateArb` + `flutter gen-l10n`** compiles against a
  missing getter. The `gradle` on PATH is 4.4.1 and cannot run the plugin — use the wrapper
  (`./gradlew`), which has the cached Gradle 8 distribution.
- **Dongle host tests need `IDF_PATH`** for cJSON (`make test IDF_PATH=~/tools/esp/esp-idf`);
  a link error about `cJSON_*` is the path, not the code. The suite expects `N tests, 0 failures`.
- **The rig's reported firmware version can be stale.** After an OTA upload check the
  `uptime_s` in `GET /api/status` dropped, not just the version string. Rollback is enabled: a
  boot that never reaches WiFi + web server reverts to the previous image, so "my change is not
  there" can mean "my change bricked the boot". Read the serial log or `/api/errors`.
- **A single failed HTTPS request on the dongle produces about five stacked ERROR lines**
  (api, HTTP_CLIENT, transport_base, two esp-tls). A deliberate connectivity test therefore
  looks alarming in the log panel; say so up front and count requests, not lines.
- **SIP injection over UDP cannot deliver a datagram above the MTU** (`CONFIG_LWIP_IP4_REASSEMBLY`
  is off; ~1500 bytes fragments and is silently dropped), and the dongle has no inbound TCP
  listener. Large-request bugs (the SIP response-buffer class) are reachable only from the
  registrar side — validate them with the host tests, not on the device.
- **`fake-call.py` must send a bare number.** A display name trips `is_known_contact()` and the
  call skips the blocklist/API check; your "blocklist is broken" probe is then probing the
  contact path.
- **A C3 Super Mini plugged into the PC's USB loses WiFi.** Association fails at weak signal
  with the 8.5 dBm cap; a rig that disappears when you move it is RF, not firmware.
- **A dongle coredump decoded against the wrong ELF** yields plausible-looking nonsense frames.
  Always pull the exact-version ELF from the CDN (`dongle-crash-analysis` has the flow).
- **Phone numbers in tests must be synthetic.** The reporter's real number, email or device
  UUID never goes into a test, a commit message, a design doc or an issue comment — derive a
  synthetic one that keeps only the structural property that triggers the bug. If real PII
  landed in an unpushed commit, rewrite it before pushing.
- **`curl -d` against a servlet needs `-H 'Content-Type: application/json'`** when the handler
  reads a JSON body; without it the body arrives as form parameters and the servlet answers 400.
  That is the probe, not the server.
- **The Bash tool resets its working directory between calls.** Use absolute paths or one `cd`
  per command; never `git add -A` after a relative `cd ..`.
- **`pkill -f <pattern>` matches your own shell** when the pattern appears in the command line
  you are running. Stop a local Jetty by port instead:
  `kill $(ss -ltnp | grep ':8080' | sed -E 's/.*pid=([0-9]+).*/\1/')`, in its own call.
- **The Playwright MCP writes into the repository root** (`.playwright-mcp/`), not the
  scratchpad — never `git add -A` while a browser session is open in another agent, and delete
  its screenshots before `git add`.
- **A merge is not gated by the commits it merges.** Run the gate (§5) on the merged tree before
  pushing, even when every branch was green on its own. Generated code, `CMakeLists.txt` and
  `scheduler.c`/`web.c` are exactly where dongle merges break (CLAUDE.md records the history).

When a probe legitimately fails: send it back (§2). When it exposes something deeper than the
package (a protocol gap, a format hazard, a fleet-compatibility break), fix small ones yourself
with a regression test; spawn a package for large ones. Classics to stay paranoid about: silent
refusals (nothing may decline without a visible message — on the dongle, WARN/ERROR is the
visible message, it lands in the web UI's log ring automatically), anything that changes what an
already-shipped dongle or mobile app will read (§8), and anything that writes a user's number
into a shared place (blocklist, phonebook, spam report) without their action.

## 5. The verification gate (before every commit)

Run every toolchain the change touches — and the Maven gate whenever `phoneblock-shared` moved,
because all Java consumers couple through it:

```bash
# Java (server, answerbot, shared, tools)
mvn -B clean install >/tmp/pb-build.log 2>&1
tail -3 /tmp/pb-build.log
grep -E '\[ERROR\]' /tmp/pb-build.log | head -20

# the Flutter app that changed
cd phoneblock_mobile && flutter analyze && flutter test            # or phoneblock_answerbot_ui

# dongle firmware
cd phoneblock-dongle/firmware/test && make test
cd phoneblock-dongle/firmware && idf.py build \
  && idf.py -B build-esp32c3 -DIDF_TARGET=esp32c3 -DSDKCONFIG=build-esp32c3/sdkconfig build \
  && idf.py -B build-esp32s3 -DIDF_TARGET=esp32s3 -DSDKCONFIG=build-esp32s3/sdkconfig build
```

All green or no commit: `BUILD SUCCESS`, analyzer with zero errors and no *new* warnings in
hand-written files, every test passing (except the known answerbot-UI starter test — say so),
both dongle targets linked, `N tests, 0 failures`. Then the **translation sync check**: a changed
German source (`de/` template, `Messages_de.properties`, `app_de.arb`, `firmware/i18n/l10n/*_de.arb`)
has its generated siblings regenerated and in the same commit. Never trust a green result from a
tree with uncommitted foreign edits; check `git status` first. A change to the API also passes
`python3 -c "import json; json.load(open('phoneblock/src/main/webapp/api/phoneblock.json'))"`.

## 6. Committing and the PR

- **Feature branch → pull request → merge commit into `master`.** The history is a chain of
  `Merge pull request #N from haumacher/<branch>` commits; keep it that way. Push the branch,
  `gh pr create -R haumacher/phoneblock`, then merge it yourself — the user does not merge — with
  a merge commit (`gh pr merge --merge --delete-branch`) after the gate ran on the merged tree
  (if `origin/master` moved since the branch was cut, merge it in and rerun the gate first).
  Pull `master` afterwards and give the user the merge commit and the PR link.
- House style for the commit: a component prefix and an imperative title
  (`Dongle: …`, `Dongle web UI: …`, `release.sh: …`, `CardDAV: …`), then paragraphs of the
  *why* and the as-built mechanism (a short bullet list of behaviors is fine; a changelog is
  not), the issue as `(#N)` in the title or `Closes #N` in the body, ending with the
  Co-Authored-By line from the harness rules.
- House style for the PR body: the problem in one paragraph, the rule or design in one
  sentence, the behaviors as bullets (a table when states × actions), a **Tested** paragraph
  naming what ran where (host tests, both dongle targets, the rig, pb-test), the harness's
  generated-with line last.
- The agents don't commit; you do, after review. Regenerated code rides the same commit as the
  German source or `.proto` change that produced it.
- **Release notes are part of the change**, not the release: a user-visible server change adds
  its bullet to the upcoming `release-notes/<version>.md`; a dongle change to
  `phoneblock-dongle/firmware/release-notes/<version>.md` (the release script refuses a version
  without one); a mobile change to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
  Keep each bullet to a few lines: what a user noticed, what changed. The analysis belongs in
  the commit message.

## 6a. Releasing

Releases are the user's action — each needs credentials you do not hold (the KeePassXC OTA
signing key, the `phoneblock.net` SSH deploy, the Play Store keystore). Your job is to make a
release *ready* and say so:

- **Server**: notes file complete, `gh issue list` holds nothing blocking, master gate green.
  The user runs `phoneblock/bin/release.sh` (`release:prepare` tags `<version>`; the
  `server-release.yml` workflow attaches the WAR to the GitHub release) and then
  `ssh phoneblock.net sudo pb-deploy <version> pb-test`, later `... phoneblock`. `pb-deploy`
  stops only that context via the Tomcat manager, backs up the H2 files and the current WAR,
  exchanges the WAR, waits for autoDeploy to bring the new version up and then follows the
  context's log; `--rollback <context> [--with-db]` undoes it. `deploy-test.sh` pushes a snapshot of
  the working tree to pb-test the same way.
- **Dongle**: follow `phoneblock-dongle/RELEASE.md`. Branch `dongle-X.Y.x` at the rc tag
  *immediately* (CLAUDE.md); notes at `firmware/release-notes/X.Y.Z.md` must exist before
  `dongle-vX.Y.Z-rc1` is cut; `release.sh` builds, signs, uploads and flips the channel
  manifest, pushing the tag only after the upload succeeded. New languages or strings reach the
  fleet via `scripts/i18n-assets.sh`, not a firmware release.
- **Mobile**: bump `version:` in `pubspec.yaml`, add the fastlane changelog for the new
  versionCode, tag `pb-mobile-<version>`; the GitHub Action builds and publishes the release.

## 7. The run loop

Each component has its own loop; run the one that changed, in this order:

```bash
# server — http://localhost:8080/phoneblock (needs phoneblock/.phoneblock; bin/setup-local.sh creates it)
cd phoneblock && mvn jetty:run
# answerbot UI inside the server: build first, Maven embeds build/web at /ab
cd phoneblock_answerbot_ui && flutter build web --base-href /phoneblock/ab/
# mobile — emulator or device with debugging enabled
cd phoneblock_mobile && flutter run
# dongle — host tests, then the real device
cd phoneblock-dongle/firmware/test && make test
```

**The dongle rig** `http://answerbot` is a live ESP32 on the LAN with auth disabled. Before any
reflash **capture the evidence first** — `/api/errors` is a RAM ring and the reboot erases it:

```bash
curl -s http://answerbot/api/errors  > $TMPDIR/ring-$(date +%s).json
curl -s http://answerbot/api/status  > $TMPDIR/status.json
curl -s -X POST --data-binary @phoneblock-dongle/firmware/build/phoneblock_dongle.bin \
     -H 'Content-Type: application/octet-stream' http://answerbot/api/firmware   # unsigned OTA, reboots
phoneblock-dongle/firmware/scripts/fake-call.py 0301234567 answerbot          # synthetic caller, bare number
```

Then read `/api/calls` or the web UI's "Letzte Anrufe" panel. QEMU recipes for paths that need no
hardware are in `firmware/README.md`; a software reset crashes the emulator, so reboot scenarios
are device-only. The rig is shared with the user: never flash while they are reading its panel,
print the *whole* captured ring, and restore any config you changed (e.g. `sip_transport`).

**pb-test** (`https://phoneblock.net/pb-test/`) is the deployed test instance reading live
data; `phoneblock/bin/deploy-test.sh` builds the working tree and hands the WAR to `pb-deploy`
on the server; production keeps running, only the pb-test context is stopped and redeployed. Tell the user the commit hash, the PR
link and what to try. Never leave a broken build running — the served instance is the shared
reference for bug reports.

## 8. Data and format doctrines (enforce in every brief and review)

- **The server talks to a fleet it cannot update.** Dongles run months-old firmware, mobile apps
  are updated at the store's pace, Fritz!Boxes sync CardDAV on their own schedule. Every API
  response, blocklist format (text and binary), CardDAV rendering and auth flow is a persisted
  contract: additive changes only, old readers keep working, and a test loads a fixture written
  by the older build. In-build round-trips prove nothing across builds.
- **German is the single source for every user-facing string**: `de/` templates,
  `Messages_de.properties`, `app_de.arb`, `firmware/i18n/l10n/*_de.arb`, `announcement-<lang>.txt`
  next to its audio. Everything else is generated by the DeepL pipelines and never hand-edited.
  Inline text goes into the template; a `Messages` key only for titles, servlet strings and
  attribute values (CLAUDE.md has the decision rule).
- **The schema is migrated, never edited in place**: a new `db-migration-XX.sql`, the final state
  in `db-schema.sql`, the `PROPERTIES` version bump, the execution in `DB.java`. All timestamps
  are Unix milliseconds; all numbers are normalized with the international prefix.
- **The OpenAPI spec is part of the endpoint.** `phoneblock.json` changes in the same commit as
  the servlet; JNDI properties change together with `JNDI-CONFIGURATION.md`.
- **Dongle memory safety is by construction**: `strbuf.h` for multi-step appends, bounded single
  writes otherwise, poisoned APIs stay poisoned; intervals above a couple of minutes go through
  `ticks_util.h`; WARN/ERROR lines are user-visible, so nothing recurring every few seconds may
  log at WARN. The firmware carries **no per-language payload** and no die-temperature readout on
  the PICO-D4 — don't re-add either.
- **Dongle fixes ship release-branch-first** (CLAUDE.md): fix on `dongle-X.Y.x`, forward-merge to
  `master`; features start from the branch they ship on.
- **No real PII anywhere durable**: not in tests, fixtures, commit messages, design docs, issue
  comments or this skill. Emails become `<email>`, numbers become synthetic `030…` sequences,
  UUIDs become `device-A`.
- **Reuse the real path.** A diagnostic or "test" affordance exercises the production code
  (shared builder, same mail, same ring), never a special variant; the test for a change lands in
  the same PR, never a follow-up.
- **Refusals speak**: no route may decline silently; the web UI, the app and the dongle panel
  show the reason.
- **Everything generic**: issues are exercises of general mechanisms — if a fix is shaped like the
  bug report, it isn't done.
- **Decisions live in `docs/plans/`, issues and commit messages.** A design doc for anything
  non-trivial, the commit message as the as-built note, the issue comment as the rationale;
  deliberate cuts become issues, never silence.

## 9. Working with this user

They are an expert (Java, software architecture) and the project's author, running the
production service and testing continuously on their own dongle and Fritz!Box. Their bug reports
come as dongle log-panel excerpts, Fritz!Box screenshots, SIP traces or pasted JSON — embed them
verbatim as regressions after scrubbing PII. Their feature sketches are usually complete designs —
restate the design back crisply, adopt it, credit it in the commit message. They dislike
over-engineering and special-case paths; when unsure, do the smaller thing. They answer in German
or English; match the issue's language in GitHub, keep commit messages and PRs in English. When
they overrule a recorded decision, the reversal is documented in the issue or design doc, not
litigated. Answer their questions with the mechanism, then the plan, then queue position. Run
notices always include the commit hash, the PR link and what to try; hardware claims come with
the measurement that backs them, never from a single noisy sample.

## 10. Two agents at once — the worktree pattern

Parallel packages work when, and only when, they are physically separated:

- **Reuse the parked worktrees** `/home/haui/devel/pb-1`, `pb-2`, `pb-3` (each on its same-named
  throwaway branch at `origin/master`) before creating new ones: `git -C /home/haui/devel/pb-1
  checkout -b <branch> origin/master`. When a package is done, `cleanup-workspace` parks the
  directory again. Separate trees keep `target/`, `build/` and `build-esp32c3/` apart. The
  worktree agent uses `mvn verify`, never `install`, so it cannot overwrite the main tree's
  `~/.m2` artifacts; if it needs a changed shared module, it builds with `-am`.
- **Each generated-code source has one writer** (§3). If both packages need the same German
  source or the same `.proto`, serialize them.
- **Each worktree needs its own local config**: `phoneblock/.phoneblock` and
  `src/test/jetty/jetty-env.xml` are git-ignored; `phoneblock/bin/setup-local.sh` creates them
  from the templates, and the build's enforcer rule fails without the first.
- **Merging**: commit each branch yourself (house-style message), open one PR per branch, run
  the full gate (§5) on the merged tree before pushing — generated code and build files are
  exactly where merges break — then push and merge the PRs yourself (§6).
- After a merge, `git ls-files` the new directories the report names; a `.gitignore` rule
  (`target/`, `build/`, `build-esp32c3/`) can swallow a wrongly named source directory.
- **A session limit kills every running agent at once** (HTTP 429 mid-flight). Their uncommitted
  work is in the trees; when the limit resets, `SendMessage` each one "Resume exactly where you
  left off: <its last visible step, read from the transcript's last tool calls>" rather than
  re-dispatching. Make the stall monitor grep the transcript tail for "session limit" so the two
  cases are told apart.
