#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "dialog.h"
#include "platform.h"   // PB_DATA_PATH_CAP

// The dialog clip set on the device's storage (issue #429): where
// i18n_sync.c puts the set it downloads and where the RTP task streams the
// clips from. Lives on the shared "storage" partition (storage.h; the data
// directory on Linux) as a rarely written asset, in place of the localized
// announcement.
//
// Layout:
//   dialog-<lang>.alaw   the set from locale <lang> as published: ONE file,
//                        its clip table in the header (dialog.h), then every
//                        clip (one file instead of one per clip)
//   dialog.idx           "<lang>\n<speaker>\n" (the speaker line empty or
//                        missing for an own set), written once that file is
//                        verified, in place and its header valid: present ⇒
//                        set complete
//   dialog-speakers.json the locale's dialog index (dialog_manifest.h) as
//                        published, SHA-256 checked; the web UI lists the
//                        speakers from it (GET /api/dialog/speakers)
//
// The user's own set (POST /api/dialog) is stored the same way under the
// pseudo-locale DIALOG_OWN_LANG ("dialog-own.alaw", index line "own"); the
// daily sync never replaces it.
//
// The set is kept only while ab_mode is "dialog"; at most one set (one
// locale or the own one, one size tier) is on the device.

#define DIALOG_STORE_PATH_CAP PB_DATA_PATH_CAP   // platform.h

// Path of the set's file for `lang` (a validated locale code).
void dialog_store_file_path(char *out, size_t cap, const char *lang);

// Path of the kept dialog index.
void dialog_store_speakers_path(char *out, size_t cap);

// The kept dialog index as a NUL-terminated heap string (free() it), or NULL
// when there is none, it cannot be read or exceeds DIALOG_INDEX_MAX_BYTES.
char *dialog_store_read_speakers(void);

// Reads the clip table from the header of the file at `path`, checked
// against the file's size. True and *set's count/clip filled if valid;
// false otherwise, *set cleared.
bool dialog_store_check_path(const char *path, dialog_set_t *set);

// Reads the clip table from the header of the file of `lang`, checked
// against the file's size (dialog_header_parse()). True and *set filled
// (set->lang = lang) if the file is a valid set; false otherwise, *set
// cleared.
bool dialog_store_read_file(const char *lang, dialog_set_t *set);

// The complete set on the device: the locale the index names, its file's
// clip table read as above. False when there is none.
bool dialog_store_load(dialog_set_t *set);

// Whether a valid own set file is on the device (index or not).
bool dialog_store_has_own(void);

// The sync's view of the own set: true if a valid one is on the device —
// then the index names it (repairing an upload interrupted between file
// and index) and any other set file is gone; the own file is never
// touched. False (nothing changed) without one.
bool dialog_store_claim_own(void);

// Marks the set of `lang`, whose file is in place and valid, complete;
// `speaker` (a dialog_speaker_valid() id, or NULL / "" for none) is the
// speaker whose set it is. PB_OK or PB_FAIL.
int dialog_store_write_index(const char *lang, const char *speaker);

// Removes the index only: the file stays, but is no longer a usable set.
void dialog_store_drop_index(void);

// Removes the set file and leftover temp file of every locale but
// `keep_lang` (NULL: of every locale; its file stays, its temp file goes). The index is left alone.
// Returns the number of files removed.
int dialog_store_remove_others(const char *keep_lang);

// Removes the index, then the set file and leftover temp file of every
// locale (the own set included) and the kept dialog index. Returns the
// number of files removed.
int dialog_store_remove_all(void);

// Marks an own set upload (POST /api/dialog) as running or finished. While
// it runs, i18n_sync leaves the set alone (the upload is refused while a
// sync runs, so the two never interleave).
void dialog_store_set_upload_active(bool active);
bool dialog_store_upload_active(void);
