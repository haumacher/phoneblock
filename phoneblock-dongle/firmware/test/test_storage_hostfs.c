// Host-filesystem backend of storage.c (the Linux port's STORAGE_HOSTFS
// build): budget from statvfs, no-op GC, the shared write-buffer file
// discipline, and storage_download()'s GC-retry discipline.
//
// The data directory is a fresh temp directory: -DPHONEBLOCK_DATA_DIR (see
// the Makefile rule) reads it from PB_HOSTFS_TEST_DIR, which main() sets, so
// the test never touches /var/lib/phoneblock and parallel runs don't collide.

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "linux_test_util.h"
#include "storage.h"

// storage.c's only outbound dependency on the sync module; this test keeps
// no cached community list, so eviction never succeeds.
bool blocklist_sync_evict_community(void)
{
    return false;
}

// Failures the storage_download() retry discipline is exercised with.
static int s_attempts;

static storage_dl_t full_then_ok(void *ctx, size_t *bytes,
                                 char *err, size_t err_cap)
{
    (void)ctx;
    s_attempts++;
    if (s_attempts == 1) {
        *bytes = 1234;
        snprintf(err, err_cap, "full");
        return STORAGE_DL_FULL;   // worth the GC+retry
    }
    return STORAGE_DL_OK;
}

static storage_dl_t always_failed(void *ctx, size_t *bytes,
                                  char *err, size_t err_cap)
{
    (void)ctx;
    (void)bytes;
    s_attempts++;
    snprintf(err, err_cap, "plain failure");
    return STORAGE_DL_FAILED;   // a retry would only repeat it
}

int main(void)
{
    const char *tmp = getenv("TMPDIR");
    char dir[256];
    snprintf(dir, sizeof(dir), "%s/pb-hostfs-XXXXXX", tmp && tmp[0] ? tmp : "/tmp");
    REQUIRE(mkdtemp(dir) != NULL);
    REQUIRE(setenv("PB_HOSTFS_TEST_DIR", dir, 1) == 0);
    char replaces[300];
    snprintf(replaces, sizeof(replaces), "%s/hostfs-replace.bin", dir);
    char path[300];
    snprintf(path, sizeof(path), "%s/hostfs-test.bin", dir);

    // The budget query creates the data directory and reports the
    // filesystem's free bytes minus the flat margin — on any developer
    // machine or CI runner that is comfortably positive.
    int64_t budget = storage_community_budget();
    REQUIRE(budget > 0);

    // With plenty of space and nothing cached, making room is a no-op that
    // must not fail or remove the file it is about to replace.
    FILE *old = fopen(replaces, "wb");
    REQUIRE(old != NULL);
    REQUIRE(fwrite("old", 1, 3, old) == 3);
    fclose(old);
    storage_make_room(1024, replaces);
    REQUIRE(access(replaces, F_OK) == 0);
    unlink(replaces);

    // storage_gc() is a no-op on a plain filesystem.
    storage_gc("test", 1 << 20);

    // The shared write-buffer discipline: several small writes land as one
    // file, and close flushes the tail.
    storage_file_t file;
    REQUIRE(storage_file_open(&file, path));
    REQUIRE(storage_file_write(&file, "abc", 3));
    REQUIRE(storage_file_write(&file, "def", 3));
    REQUIRE(storage_file_close(&file));
    FILE *in = fopen(path, "rb");
    REQUIRE(in != NULL);
    char buf[8] = {0};
    REQUIRE(fread(buf, 1, 6, in) == 6);
    fclose(in);
    REQUIRE(memcmp(buf, "abcdef", 6) == 0);
    unlink(path);

    // storage_download() retries exactly once after STORAGE_DL_FULL (the GC
    // pass in between is the hostfs no-op), and never after a plain failure.
    char err[64];
    s_attempts = 0;
    REQUIRE(storage_download("test", full_then_ok, NULL, err, sizeof(err)));
    REQUIRE(s_attempts == 2);

    s_attempts = 0;
    REQUIRE(!storage_download("test", always_failed, NULL, err, sizeof(err)));
    REQUIRE(s_attempts == 1);
    REQUIRE(strcmp(err, "plain failure") == 0);

    REQUIRE(rmdir(dir) == 0);   // empty only if the test cleaned up
    puts("test_storage_hostfs: all tests passed");
    return 0;
}
