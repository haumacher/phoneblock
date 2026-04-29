#include "api_scan.h"

#include <string.h>

#include "cJSON.h"

void api_scan_init(api_scan_t *s, const char *phone)
{
    memset(s, 0, sizeof(*s));
    s->phone = phone;
    s->phone_len = (int)strlen(phone);
    s->current_array = API_SCAN_ARR_NONE;
}

static void scan_handle_object(api_scan_t *s)
{
    cJSON *o = cJSON_Parse(s->obj_buf);
    if (!o) {
        s->error = true;
        if (!s->error_reason) s->error_reason = "entry parse failed";
        return;
    }

    if (s->current_array == API_SCAN_ARR_NUMBERS) {
        const cJSON *p = cJSON_GetObjectItemCaseSensitive(o, "phone");
        if (cJSON_IsString(p) && p->valuestring &&
            strcmp(p->valuestring, s->phone) == 0) {
            const cJSON *v = cJSON_GetObjectItemCaseSensitive(o, "votes");
            if (cJSON_IsNumber(v)) s->direct_votes = v->valueint;
            const cJSON *lbl = cJSON_GetObjectItemCaseSensitive(o, "label");
            if (cJSON_IsString(lbl) && lbl->valuestring) {
                strncpy(s->label, lbl->valuestring, sizeof(s->label) - 1);
                s->label[sizeof(s->label) - 1] = '\0';
            }
            const cJSON *loc = cJSON_GetObjectItemCaseSensitive(o, "location");
            if (cJSON_IsString(loc) && loc->valuestring) {
                strncpy(s->location, loc->valuestring, sizeof(s->location) - 1);
                s->location[sizeof(s->location) - 1] = '\0';
            }
            const cJSON *wl = cJSON_GetObjectItemCaseSensitive(o, "whiteListed");
            if (cJSON_IsBool(wl)) s->white_listed = cJSON_IsTrue(wl);
            const cJSON *bl = cJSON_GetObjectItemCaseSensitive(o, "blackListed");
            if (cJSON_IsBool(bl)) s->black_listed = cJSON_IsTrue(bl);
        }
    } else if (s->current_array == API_SCAN_ARR_RANGE10 ||
               s->current_array == API_SCAN_ARR_RANGE100) {
        int expected_len = (s->current_array == API_SCAN_ARR_RANGE10)
            ? s->phone_len - 1 : s->phone_len - 2;
        if (expected_len > 0) {
            const cJSON *p = cJSON_GetObjectItemCaseSensitive(o, "prefix");
            if (cJSON_IsString(p) && p->valuestring &&
                (int)strlen(p->valuestring) == expected_len &&
                strncmp(p->valuestring, s->phone, expected_len) == 0) {
                const cJSON *v = cJSON_GetObjectItemCaseSensitive(o, "votes");
                const cJSON *c = cJSON_GetObjectItemCaseSensitive(o, "cnt");
                int votes = cJSON_IsNumber(v) ? v->valueint : 0;
                int cnt   = cJSON_IsNumber(c) ? c->valueint : 0;
                if (s->current_array == API_SCAN_ARR_RANGE10) {
                    s->v10  = votes; s->c10  = cnt;
                } else {
                    s->v100 = votes; s->c100 = cnt;
                }
            }
        }
    }

    cJSON_Delete(o);
}

void api_scan_feed(api_scan_t *s, const char *data, int len)
{
    for (int i = 0; i < len; i++) {
        char b = data[i];

        // Raw passthrough into the per-entry buffer — preserves
        // strings, whitespace, and any unknown fields verbatim for
        // cJSON.
        if (s->collecting_obj) {
            if (s->obj_len < (int)sizeof(s->obj_buf) - 1) {
                s->obj_buf[s->obj_len++] = b;
            } else {
                s->obj_overflow = true;
            }
        }

        if (s->in_string) {
            if (s->collecting_key) {
                bool literal_char =
                    s->escape_next || (b != '\\' && b != '"');
                if (literal_char &&
                    s->key_len < (int)sizeof(s->key_buf) - 1) {
                    s->key_buf[s->key_len++] = b;
                }
            }
            if (s->escape_next)         s->escape_next = false;
            else if (b == '\\')         s->escape_next = true;
            else if (b == '"') {
                s->in_string = false;
                if (s->collecting_key) {
                    s->collecting_key = false;
                    s->key_buf[s->key_len] = '\0';
                }
            }
            continue;
        }

        switch (b) {
        case '"':
            s->in_string = true;
            // Top-level keys live at brace_depth==1, bracket_depth==0.
            if (s->brace_depth == 1 && s->bracket_depth == 0 &&
                !s->collecting_obj) {
                s->collecting_key = true;
                s->key_len = 0;
            }
            break;
        case '{':
            s->brace_depth++;
            // Entry object opens at brace_depth==2 inside one of the
            // recognised top-level arrays.
            if (!s->collecting_obj && s->bracket_depth == 1 &&
                s->brace_depth == 2 &&
                s->current_array != API_SCAN_ARR_NONE) {
                s->collecting_obj = true;
                s->obj_len = 0;
                s->obj_overflow = false;
                s->obj_buf[s->obj_len++] = '{';
            }
            break;
        case '}':
            s->brace_depth--;
            if (s->collecting_obj && s->brace_depth == 1) {
                if (s->obj_overflow) {
                    s->error = true;
                    if (!s->error_reason)
                        s->error_reason = "entry exceeded buffer";
                } else {
                    s->obj_buf[s->obj_len] = '\0';
                    scan_handle_object(s);
                }
                s->collecting_obj = false;
            }
            break;
        case '[':
            s->bracket_depth++;
            if (s->brace_depth == 1 && s->bracket_depth == 1) {
                if      (strcmp(s->key_buf, "numbers")  == 0)
                    s->current_array = API_SCAN_ARR_NUMBERS;
                else if (strcmp(s->key_buf, "range10")  == 0)
                    s->current_array = API_SCAN_ARR_RANGE10;
                else if (strcmp(s->key_buf, "range100") == 0)
                    s->current_array = API_SCAN_ARR_RANGE100;
                else
                    s->current_array = API_SCAN_ARR_NONE;
            }
            break;
        case ']':
            s->bracket_depth--;
            if (s->bracket_depth == 0) s->current_array = API_SCAN_ARR_NONE;
            break;
        default:
            break;
        }
    }
}
