#pragma once

// Lightweight semver-ish comparison for our own firmware version strings
// (project version from CMakeLists.txt, e.g. "1.5.3" or "1.5.3-rc1").
// Returns <0 / 0 / >0 like strcmp.
//
// Numeric major.minor.patch compare first. When those are equal, a
// pre-release suffix ("-rc1", "-dev", …) sorts strictly *below* the same
// release without a suffix, so "1.5.3-rc1" never displaces a released
// "1.5.3". Two pre-release suffixes of the same x.y.z are ordered by their
// identifier with a natural compare, so "1.4.0-rc1" < "1.4.0-rc2" <
// "1.4.0-rc10" — this is what lets the beta channel hand out successive
// rc builds of the same version (issue #416).
//
// Pure libc, no ESP-IDF dependency, so it is exercised by the host test
// harness (test_version_cmp.c).
int version_cmp(const char *a, const char *b);
