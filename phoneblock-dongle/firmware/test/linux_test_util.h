#pragma once

// Helpers shared by the Linux platform / API tests.

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

// Like assert(), but always evaluated — also under NDEBUG — so it may wrap
// calls whose side effects the test depends on.
#define REQUIRE(cond) do {                                              \
    if (!(cond)) {                                                      \
        fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
        exit(1);                                                        \
    }                                                                   \
} while (0)

// Creates a fresh, empty file under $TMPDIR (or /tmp) for the key-value
// store and points PHONEBLOCK_CONFIG_FILE at it. The caller unlinks `path`.
static inline void use_temp_config(char *path, size_t cap)
{
    const char *dir = getenv("TMPDIR");
    snprintf(path, cap, "%s/phoneblock-test-XXXXXX",
             dir && dir[0] ? dir : "/tmp");
    int fd = mkstemp(path);
    REQUIRE(fd >= 0);
    close(fd);
    REQUIRE(setenv("PHONEBLOCK_CONFIG_FILE", path, 1) == 0);
}

// Opens the persistent call list (stats.c, #589) in a fresh temp directory
// `dir`; the file is `path`. The caller removes both. Declared here, not by
// including stats.h, so tests that do not link stats.c are not affected.
#include <stdbool.h>
bool stats_calls_open(const char *path);
static inline void use_temp_calls(char *dir, size_t dir_cap, char *path, size_t path_cap)
{
    const char *tmp = getenv("TMPDIR");
    snprintf(dir, dir_cap, "%s/phoneblock-calls-XXXXXX", tmp && tmp[0] ? tmp : "/tmp");
    REQUIRE(mkdtemp(dir) != NULL);
    snprintf(path, path_cap, "%s/calls.bin", dir);
    REQUIRE(stats_calls_open(path));
}
