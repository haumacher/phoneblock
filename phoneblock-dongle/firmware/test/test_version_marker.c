#define _POSIX_C_SOURCE 200809L

// Host test for version_marker.c: the Linux service's "first start of this
// version" signal that lets the scheduler fetch the new version's language
// packs early after a package update (#721).

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "linux_test_util.h"
#include "version_marker.h"

// The marker's content, or "" if it cannot be read.
static const char *read_marker(const char *path)
{
    static char line[128];
    line[0] = '\0';
    FILE *f = fopen(path, "r");
    if (!f) return line;
    if (!fgets(line, sizeof(line), f)) line[0] = '\0';
    fclose(f);
    return line;
}

static void write_marker(const char *path, const char *content)
{
    FILE *f = fopen(path, "w");
    REQUIRE(f != NULL);
    REQUIRE(fputs(content, f) >= 0);
    REQUIRE(fclose(f) == 0);
}

int main(void)
{
    const char *tmp = getenv("TMPDIR");
    char dir[256];
    snprintf(dir, sizeof(dir), "%s/phoneblock-marker-XXXXXX", tmp && tmp[0] ? tmp : "/tmp");
    REQUIRE(mkdtemp(dir) != NULL);
    char path[300], tmp_path[310];
    snprintf(path, sizeof(path), "%s/%s", dir, VERSION_MARKER_FILE);
    snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", path);
    bool stored = false;

    // No marker yet (fresh install, or the first version with the marker):
    // a change, and the version is stored.
    REQUIRE(version_marker_changed(path, "2.0.0-beta1", &stored));
    REQUIRE(stored);
    REQUIRE(strcmp(read_marker(path), "2.0.0-beta1\n") == 0);
    REQUIRE(access(tmp_path, F_OK) != 0);

    // Restart of the same version: no change, marker untouched.
    stored = true;
    REQUIRE(!version_marker_changed(path, "2.0.0-beta1", &stored));
    REQUIRE(!stored);
    REQUIRE(strcmp(read_marker(path), "2.0.0-beta1\n") == 0);

    // Package update: a change, the new version replaces the old one.
    REQUIRE(version_marker_changed(path, "2.0.0-beta2", &stored));
    REQUIRE(stored);
    REQUIRE(strcmp(read_marker(path), "2.0.0-beta2\n") == 0);
    REQUIRE(!version_marker_changed(path, "2.0.0-beta2", NULL));

    // A downgrade is a change too.
    REQUIRE(version_marker_changed(path, "1.7.0-beta9", NULL));
    REQUIRE(strcmp(read_marker(path), "1.7.0-beta9\n") == 0);

    // A marker without newline (written by hand) or with CRLF still matches.
    write_marker(path, "1.7.0-beta9");
    REQUIRE(!version_marker_changed(path, "1.7.0-beta9", NULL));
    write_marker(path, "1.7.0-beta9\r\n");
    REQUIRE(!version_marker_changed(path, "1.7.0-beta9", NULL));

    // An empty or a longer-than-buffer marker reads as a change and is rewritten.
    write_marker(path, "");
    REQUIRE(version_marker_changed(path, "1.7.0-beta9", &stored));
    REQUIRE(stored);
    char longline[200];
    memset(longline, 'x', sizeof(longline) - 1);
    longline[sizeof(longline) - 1] = '\0';
    write_marker(path, longline);
    REQUIRE(version_marker_changed(path, "1.7.0-beta9", NULL));
    REQUIRE(strcmp(read_marker(path), "1.7.0-beta9\n") == 0);

    // The marker cannot be written (directory gone): still reported as a
    // change, so the early fetch happens; `stored` says the write failed.
    char gone[310];
    snprintf(gone, sizeof(gone), "%s/missing/%s", dir, VERSION_MARKER_FILE);
    stored = true;
    REQUIRE(version_marker_changed(gone, "2.0.0", &stored));
    REQUIRE(!stored);

    // No path or version: nothing to compare, no change.
    REQUIRE(!version_marker_changed(NULL, "2.0.0", NULL));
    REQUIRE(!version_marker_changed(path, NULL, NULL));

    REQUIRE(remove(path) == 0);
    REQUIRE(rmdir(dir) == 0);
    printf("test_version_marker: all tests passed\n");
    return 0;
}
