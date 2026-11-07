#pragma once

#include <stdbool.h>

// File name of the marker in the data directory (PHONEBLOCK_DATA_DIR).
#define VERSION_MARKER_FILE "last-version"

// The last firmware version that started, kept in a small file in the data
// directory. The Linux service uses it to tell the first start after a
// package update, which the ESP32 reads from its OTA image state instead
// (main.c, fw_just_updated): then the scheduler fetches the new version's
// language packs within seconds instead of after the usual 3 minutes, so
// the web UI does not keep serving the previous version's pack (#721).
//
// Returns true when `version` differs from the version stored at `path`,
// or nothing (readable) is stored there yet, and then stores `version`
// (written to "<path>.tmp" and renamed, so a crash never leaves a torn
// file). A missing file counts as changed: it is either a fresh install,
// where an early fetch is just as right, or the first start of the version
// that introduced the marker. `stored` (optional) reports whether that
// write succeeded; when it did not, the next start reports a change again.
//
// Pure libc, host-tested in test/test_version_marker.c.
bool version_marker_changed(const char *path, const char *version, bool *stored);
