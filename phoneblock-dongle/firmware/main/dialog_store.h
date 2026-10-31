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
//   dialog-<lang>.alaw   every clip of the set, from locale <lang>, as ONE
//                        file (one SPIFFS object instead of one per clip)
//   dialog.idx           the clip table (dialog_set_format()), written after
//                        the file is in place: present ⇒ set complete
//
// The set is kept only while ab_mode is "dialog"; at most one set (one
// locale) is on the device.

#ifdef ESP_PLATFORM
#define DIALOG_STORE_PATH_CAP 64
#else
#define DIALOG_STORE_PATH_CAP 512
#endif

// Path of the set's file for `lang` (a validated locale code).
void dialog_store_file_path(char *out, size_t cap, const char *lang);

// Reads the index. True if it names a complete set; with `check_file` the
// set's file must also be there and hold every clip of the table (the call
// path does that, the status poll does not). False when there is no set.
bool dialog_store_load(dialog_set_t *set, bool check_file);

// Writes the index for a set whose file is in place. PB_OK or PB_FAIL.
int dialog_store_write_index(const dialog_set_t *set);

// Removes the index only: the file stays, but is no longer a usable set.
void dialog_store_drop_index(void);

// Removes the set file and leftover temp file of every locale but
// `keep_lang` (its file stays, its temp file goes). The index is left alone.
// Returns the number of files removed.
int dialog_store_remove_others(const char *keep_lang);

// Removes the index, then the set file and leftover temp file of every
// locale. Returns the number of files removed.
int dialog_store_remove_all(void);
