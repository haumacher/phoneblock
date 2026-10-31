#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "dialog.h"

// The dialog clip set on the device's storage (issue #429): where
// i18n_sync.c puts the set it downloads and where the RTP task streams the
// clips from. Lives on the shared "storage" SPIFFS partition (the data
// directory on Linux) as a rarely written asset, in place of the localized
// announcement.
//
// Layout:
//   dialog-<lang>.alaw   the set from locale <lang> as published: ONE file,
//                        its clip table in the header (dialog.h), then every
//                        clip (one SPIFFS object instead of one per clip)
//   dialog.idx           "<lang>\n", written once that file is verified, in
//                        place and its header valid: present ⇒ set complete
//
// The set is kept only while ab_mode is "dialog"; at most one set (one
// locale, one size tier) is on the device.

#ifdef ESP_PLATFORM
#define DIALOG_STORE_PATH_CAP 64
#else
#define DIALOG_STORE_PATH_CAP 512
#endif

// Path of the set's file for `lang` (a validated locale code).
void dialog_store_file_path(char *out, size_t cap, const char *lang);

// Reads the clip table from the header of the file of `lang`, checked
// against the file's size (dialog_header_parse()). True and *set filled
// (set->lang = lang) if the file is a valid set; false otherwise, *set
// cleared.
bool dialog_store_read_file(const char *lang, dialog_set_t *set);

// The complete set on the device: the locale the index names, its file's
// clip table read as above. False when there is none.
bool dialog_store_load(dialog_set_t *set);

// Marks the set of `lang`, whose file is in place and valid, complete.
// PB_OK or PB_FAIL.
int dialog_store_write_index(const char *lang);

// Removes the index only: the file stays, but is no longer a usable set.
void dialog_store_drop_index(void);

// Removes the set file and leftover temp file of every locale but
// `keep_lang` (its file stays, its temp file goes). The index is left alone.
// Returns the number of files removed.
int dialog_store_remove_others(const char *keep_lang);

// Removes the index, then the set file and leftover temp file of every
// locale. Returns the number of files removed.
int dialog_store_remove_all(void);
