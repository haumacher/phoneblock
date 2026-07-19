#pragma once

// Localized status-mail strings (issue #460). The German strings are the
// compiled-in fallback (and the source of truth mirrored in
// scripts/i18n/mail.de.json); other locales come from a pack downloaded by
// i18n_sync.c to /spiffs/mail-<lang>.json for the active ui_lang.
//
// mail_i18n_str(key) returns the string for the current ui_lang: the
// downloaded pack's value when present and valid, otherwise the compiled
// German fallback, otherwise the key itself (so a missing key is visible in
// the mail rather than crashing). Values may carry printf specifiers and
// small HTML fragments — see scripts/i18n/mail.de.json for the contract.
//
// Called only from the mail build path, which runs single-threaded on the
// scheduler task; the pack is lazily loaded and cached, and reloaded when
// the locale changes. Returned pointers are valid until the next locale
// change, i.e. for the duration of one mail build.
const char *mail_i18n_str(const char *key);
