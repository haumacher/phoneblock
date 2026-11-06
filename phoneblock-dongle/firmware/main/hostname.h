#pragma once

// The dongle's host name on the LAN (#714): the DHCP host name (option 12)
// and the mDNS name `<name>.local`. Several dongles on one network need
// different names, otherwise they all claim `answerbot`.
//
// Pure functions, no platform dependencies (host-tested in
// test/test_hostname.c).

#include <stdbool.h>
#include <stddef.h>

// The name every dongle announces until the user picks another one. An empty
// stored value means this default, so devices that predate the setting keep
// their name.
#define HOSTNAME_DEFAULT "answerbot"

// Longest accepted name. RFC 1123 allows 63 per label; 32 keeps the name
// short enough to type and fits the Fritz!Box's device list.
#define HOSTNAME_MAX_LEN 32
#define HOSTNAME_CAP     (HOSTNAME_MAX_LEN + 1)

// True if `name` is a well-formed host name as stored: one RFC 1123 label of
// 1..HOSTNAME_MAX_LEN characters from [a-z0-9-], not starting or ending with
// a hyphen. Upper case is refused here (hostname_normalize() lowers it).
bool hostname_valid(const char *name);

// Turns user input into the value to store. Surrounding whitespace is
// dropped and ASCII letters are lower-cased. Returns true and writes the
// result to out (out_cap >= HOSTNAME_CAP) if it is either empty — "reset to
// the default" — or hostname_valid(). Returns false for anything else
// (underscore, dot, non-ASCII, too long, leading or trailing hyphen); out is
// then set to "".
bool hostname_normalize(const char *in, char *out, size_t out_cap);
