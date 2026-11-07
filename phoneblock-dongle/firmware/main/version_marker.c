#include "version_marker.h"

#include <stdio.h>
#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Room for a version string ("2.0.0-beta1", git-describe forms with a
// hash and "-dirty"); a longer stored line simply reads as a change.
#define VERSION_MARKER_CAP 64

static bool store(const char *path, const char *version)
{
    char tmp[512];
    int n = snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    if (n < 0 || (size_t)n >= sizeof(tmp)) return false;
    FILE *f = fopen(tmp, "w");
    if (!f) return false;
    bool ok = fprintf(f, "%s\n", version) >= 0;
    ok = (fclose(f) == 0) && ok;
    if (ok && rename(tmp, path) != 0) ok = false;
    if (!ok) remove(tmp);
    return ok;
}

bool version_marker_changed(const char *path, const char *version, bool *stored)
{
    if (stored) *stored = false;
    if (!path || !version) return false;

    char last[VERSION_MARKER_CAP] = "";
    FILE *f = fopen(path, "r");
    if (f) {
        if (!fgets(last, sizeof(last), f)) last[0] = '\0';
        fclose(f);
        last[strcspn(last, "\r\n")] = '\0';
    }
    if (f && last[0] && strcmp(last, version) == 0) return false;

    bool ok = store(path, version);
    if (stored) *stored = ok;
    return true;
}
