// Host test for version_cmp.c — the firmware version ordering that the
// OTA manifest decision relies on. Pure libc.
#include <assert.h>
#include <stdio.h>

#include "version_cmp.h"

// Sign of version_cmp, so the assertions read as -1 / 0 / +1.
static int sgn(int v) { return (v > 0) - (v < 0); }

int main(void)
{
    // --- major.minor.patch ordering ---
    assert(sgn(version_cmp("1.4.1", "1.4.0")) == 1);
    assert(sgn(version_cmp("1.4.0", "1.4.1")) == -1);
    assert(sgn(version_cmp("2.0.0", "1.9.9")) == 1);
    assert(sgn(version_cmp("1.5.0", "1.4.9")) == 1);
    assert(sgn(version_cmp("1.4.0", "1.4.0")) == 0);

    // --- issue #416: successive rc builds of the same x.y.z must order ---
    // The beta channel hands out 1.4.0-rc1, then 1.4.0-rc2; a dongle on
    // rc1 must see rc2 as newer (previously this returned 0 → no update).
    assert(sgn(version_cmp("1.4.0-rc2", "1.4.0-rc1")) == 1);
    assert(sgn(version_cmp("1.4.0-rc1", "1.4.0-rc2")) == -1);
    assert(sgn(version_cmp("1.4.0-rc1", "1.4.0-rc1")) == 0);
    // Natural (numeric) ordering, not lexicographic: rc2 < rc10.
    assert(sgn(version_cmp("1.4.0-rc10", "1.4.0-rc2")) == 1);
    assert(sgn(version_cmp("1.4.0-rc2", "1.4.0-rc10")) == -1);

    // --- a pre-release sorts below the same released version ---
    assert(sgn(version_cmp("1.4.0-rc1", "1.4.0")) == -1);
    assert(sgn(version_cmp("1.4.0", "1.4.0-rc1")) == 1);

    // --- a released build still beats a pre-release of a lower version ---
    assert(sgn(version_cmp("1.4.0", "1.3.9-rc5")) == 1);
    // ...and a pre-release of a higher version beats a lower release.
    assert(sgn(version_cmp("1.5.0-rc1", "1.4.9")) == 1);

    // --- non-numeric pre-release identifiers fall back to byte order ---
    assert(sgn(version_cmp("1.4.0-rc1", "1.4.0-dev")) == 1); // 'd' < 'r'

    printf("test_version_cmp: OK\n");
    return 0;
}
