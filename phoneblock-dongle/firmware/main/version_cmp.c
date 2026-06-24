#include "version_cmp.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

// Natural compare of two pre-release identifiers (the part after the
// first '-', e.g. "rc1", "rc2", "rc10", "dev"). Non-digit runs compare
// byte-wise; digit runs compare numerically (leading zeros ignored), so
// "rc2" < "rc10" rather than the lexicographic "rc10" < "rc2". Returns
// <0 / 0 / >0 like strcmp.
static int prerelease_cmp(const char *a, const char *b)
{
    while (*a && *b) {
        if (isdigit((unsigned char)*a) && isdigit((unsigned char)*b)) {
            while (*a == '0') a++;
            while (*b == '0') b++;
            const char *as = a, *bs = b;
            while (isdigit((unsigned char)*a)) a++;
            while (isdigit((unsigned char)*b)) b++;
            size_t alen = (size_t)(a - as), blen = (size_t)(b - bs);
            if (alen != blen) return alen < blen ? -1 : 1;
            int c = strncmp(as, bs, alen);
            if (c != 0) return c < 0 ? -1 : 1;
        } else {
            if (*a != *b) {
                return (unsigned char)*a < (unsigned char)*b ? -1 : 1;
            }
            a++;
            b++;
        }
    }
    if (*a) return 1;
    if (*b) return -1;
    return 0;
}

int version_cmp(const char *a, const char *b)
{
    int aMaj = 0, aMin = 0, aPat = 0;
    int bMaj = 0, bMin = 0, bPat = 0;
    sscanf(a, "%d.%d.%d", &aMaj, &aMin, &aPat);
    sscanf(b, "%d.%d.%d", &bMaj, &bMin, &bPat);
    if (aMaj != bMaj) return aMaj < bMaj ? -1 : 1;
    if (aMin != bMin) return aMin < bMin ? -1 : 1;
    if (aPat != bPat) return aPat < bPat ? -1 : 1;

    const char *aPre = strchr(a, '-');
    const char *bPre = strchr(b, '-');
    // A final release (no suffix) sorts above any pre-release of the
    // same x.y.z.
    if (!aPre && !bPre) return 0;
    if (!aPre) return 1;
    if (!bPre) return -1;
    // Both are pre-releases of the same x.y.z: order by the identifier
    // ("rc1" < "rc2" < "rc10").
    return prerelease_cmp(aPre + 1, bPre + 1);
}
