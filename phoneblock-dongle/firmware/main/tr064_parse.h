#pragma once

#include <stdbool.h>
#include <stddef.h>

// Pure-C parser helpers used by tr064.c. Kept free of ESP-IDF
// dependencies so the host-based test harness can exercise them.

// Find the first element whose local name matches `tag` (ignoring
// any namespace prefix) and copy its text content into `out`.
// Returns the content length on success, -1 if the tag was not
// found. Copies at most `cap - 1` bytes and always NUL-terminates.
int tr064_xml_find_text(const char *xml, const char *tag,
                        char *out, size_t cap);

// In-place decode of the five predefined XML entities (&amp;, &lt;,
// &gt;, &quot;, &apos;). The decoded string is never longer than
// the input, so doing this in place is safe.
void tr064_xml_unescape_inplace(char *s);

// Escape the five predefined XML entities when copying `in` to
// `out`. Truncates at `cap - 1` bytes and always NUL-terminates.
void tr064_xml_escape(const char *in, char *out, size_t cap);

// Pick the default login username from an X_AVM-DE_GetUserList
// response body (entity-decoded). Matches either the attribute form
// `<Username last_user="1">name</Username>` or the legacy wrapper
// form `<User><Username>name</Username><LastUser>1</LastUser></User>`.
// Falls back to the first <Username> element if no marker is
// present. Returns true on success and writes the username to `out`.
bool tr064_pick_default_user(char *xml, char *out, size_t cap);

// Walk a Fritz!Box phonebook XML and invoke `cb()` for each contact
// entry, passing its uniqueid and first number. Handles
// "<contact>" as well as "<contact …>"-with-attributes; inner
// <number …> and <uniqueid> are parsed through the attribute-
// aware tr064_xml_find_text. Returns the number of contacts for
// which both fields were non-empty.
//
// `xml` must be mutable (the implementation NUL-terminates each
// <contact>…</contact> block temporarily).
typedef void (*tr064_contact_cb_t)(const char *uid, const char *number,
                                   void *user);
int tr064_parse_phonebook_contacts(char *xml, int xml_len,
                                   tr064_contact_cb_t cb, void *user);
