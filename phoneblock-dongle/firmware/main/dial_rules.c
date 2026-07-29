#include "dial_rules.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Countries whose dialling grammar differs from the "0" trunk / "00"
// international majority, as "<cc>:<trunk>:<intl>" tokens separated by
// single spaces. Generated from the server's
// location/trunk-prefixes.csv (the same source the website uses), keyed by
// country calling code rather than by ISO country: every country sharing a
// calling code shares its dialling rules.
//
// The one exception in the source data is +1, where two of the 24 NANP
// territories carry a different international escape (Sint Maarten "00",
// Turks and Caicos "0") than the other 22 ("011"); resolved by majority.
//
// An empty trunk field means the country has none.
static const char TABLE[] =
    "1:1:011 7:8:810 30::00 34::00 36:06:00 39::00 45::00 47::00 48::00 "
    "52:01:00 53:0:119 55:0:0014 56::1230 57:0:005 61:0:0011 62:0:001 "
    "65::001 66:0:001 81:0:010 82:0:001 211::00 216::00 220::00 221::00 "
    "222::00 223::00 224::00 225::00 226::00 227::00 228::00 229::00 "
    "230::00 231::00 234:0:009 235::00 236::00 237::00 238::00 239::00 "
    "240::00 241::00 242::00 244::00 245::00 246::00 247::00 248::00 "
    "250::00 252::00 253::00 254:0:000 255:0:000 256:0:000 257::00 "
    "258::00 265::00 266::00 267::00 268::00 269::00 290::00 297::00 "
    "298::00 299::00 350::00 351::00 352::00 354::00 356::00 357::00 "
    "370:8:00 371::00 372::00 375:80:810 376::00 377::00 378::00 379::00 "
    "420::00 423::00 500::00 501::00 502::00 503::00 504::00 505::00 "
    "506::00 507::00 508::00 509::00 592::001 670::00 673::00 674::00 "
    "675::00 676::00 677::00 678::00 679::00 680::011 681::00 682::00 "
    "683::00 685::0 686::00 687::00 688::00 689::00 690::00 691:1:011 "
    "692:1:011 850::99 852::001 853::00 855:0:001 870::00 886:0:002 "
    "960::00 964::00 965::00 968::00 973::00 974::00 975::00 976:0:001 "
    "992:8:810 993:8:810 6723::00 8816::00 8817::00 88216::00";

// Copy up to `cap`-1 bytes from `src` until `stop` or end of string, then
// return the position of the terminator (an over-long field is truncated in
// `dst` but still skipped in full).
static const char *copy_field(const char *src, char stop, char *dst, size_t cap)
{
    size_t n = 0;
    while (*src && *src != stop) {
        if (n < cap - 1) dst[n++] = *src;
        src++;
    }
    dst[n] = '\0';
    return src;
}

dial_rules_t dial_rules_for(const char *dial_prefix)
{
    dial_rules_t out = { .trunk = "0", .intl = "00" };

    if (!dial_prefix || *dial_prefix != '+') return out;
    const char *cc = dial_prefix + 1;
    size_t cc_len = strlen(cc);
    if (cc_len == 0 || cc_len > 5) return out;

    for (const char *p = TABLE; *p; ) {
        // Entry starts at p; match "<cc>:" against the wanted code.
        if (strncmp(p, cc, cc_len) == 0 && p[cc_len] == ':') {
            // Trunk goes straight into the result — empty is a valid value
            // there. The escape goes via a temporary, because an empty one
            // must leave the "00" default in place: callers use it as a
            // prefix test and "" would match every number.
            char intl[sizeof(out.intl)];
            const char *f = copy_field(p + cc_len + 1, ':',
                                       out.trunk, sizeof(out.trunk));
            if (*f == ':') f++;
            copy_field(f, ' ', intl, sizeof(intl));
            if (intl[0]) memcpy(out.intl, intl, sizeof(intl));
            return out;
        }
        // Advance to the next entry.
        while (*p && *p != ' ') p++;
        while (*p == ' ') p++;
    }
    return out;
}
