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
