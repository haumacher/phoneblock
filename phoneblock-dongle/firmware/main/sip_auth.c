#include "sip_auth.h"

#include <string.h>
#include <strings.h>

static void copy_value(const char *src, size_t src_len, char *dst, size_t dst_cap)
{
    size_t n = src_len < dst_cap - 1 ? src_len : dst_cap - 1;
    memcpy(dst, src, n);
    dst[n] = '\0';
}

void sip_auth_parse_challenge(const char *header_value, auth_challenge_t *out)
{
    memset(out, 0, sizeof(*out));
    strcpy(out->algorithm, "MD5");

    const char *p = header_value;
    while (*p == ' ') p++;
    if (strncasecmp(p, "Digest", 6) == 0) {
        p += 6;
        while (*p == ' ') p++;
    }

    while (*p) {
        while (*p == ' ' || *p == ',' || *p == '\t') p++;
        if (!*p) break;

        const char *key = p;
        while (*p && *p != '=' && *p != ',') p++;
        size_t key_len = (size_t)(p - key);
        if (*p != '=') {
            // Malformed pair; skip to next comma.
            while (*p && *p != ',') p++;
            continue;
        }
        p++;  // '='

        const char *val;
        size_t val_len;
        if (*p == '"') {
            p++;
            val = p;
            while (*p && *p != '"') p++;
            val_len = (size_t)(p - val);
            if (*p == '"') p++;
        } else {
            val = p;
            while (*p && *p != ',' && *p != ' '
                       && *p != '\r' && *p != '\n') p++;
            val_len = (size_t)(p - val);
        }

        if (key_len == 5 && strncasecmp(key, "realm", 5) == 0) {
            copy_value(val, val_len, out->realm, sizeof(out->realm));
        } else if (key_len == 5 && strncasecmp(key, "nonce", 5) == 0) {
            copy_value(val, val_len, out->nonce, sizeof(out->nonce));
        } else if (key_len == 6 && strncasecmp(key, "opaque", 6) == 0) {
            copy_value(val, val_len, out->opaque, sizeof(out->opaque));
        } else if (key_len == 3 && strncasecmp(key, "qop", 3) == 0) {
            // qop may be a list; pick "auth" if offered, else stay empty.
            char qvals[32];
            copy_value(val, val_len, qvals, sizeof(qvals));
            if (strstr(qvals, "auth-int")
                && !strstr(qvals, "auth,") && !strstr(qvals, "auth ")) {
                strcpy(out->qop, "");
            } else if (strstr(qvals, "auth")) {
                strcpy(out->qop, "auth");
            }
        } else if (key_len == 9 && strncasecmp(key, "algorithm", 9) == 0) {
            copy_value(val, val_len, out->algorithm, sizeof(out->algorithm));
        }
    }

    out->valid = out->realm[0] != '\0' && out->nonce[0] != '\0';
}

const char *sip_auth_effective_realm(const char *override,
                                     const auth_challenge_t *challenge)
{
    if (override && override[0]) return override;
    return challenge->realm;
}

const char *sip_auth_effective_user(const char *override,
                                    const char *identity_user)
{
    if (override && override[0]) return override;
    return identity_user;
}
