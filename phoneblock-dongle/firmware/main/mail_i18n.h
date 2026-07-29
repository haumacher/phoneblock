#pragma once

// Localized status-mail strings (issue #460). The English pack (built from
// i18n/l10n/mail/mail_en.arb) is baked into the firmware as the offline
// fallback; other locales — including German — come from a pack downloaded by
// i18n_sync.c to /spiffs/mail-<lang>.json for the active ui_lang. The German
// source mail_de.arb is the normative edit source translated into the rest.
//
// mail_i18n_str(key) returns the string for the current ui_lang: the
// downloaded pack's value when present and valid, otherwise the embedded
// English fallback, otherwise the key itself (so a missing key is visible in
// the mail rather than crashing). Values use ICU {name} placeholders and may
// carry small HTML fragments — see mail_de.arb / mail_en.arb for the contract.
//
// Called only from the mail build path, which runs single-threaded on the
// scheduler task; the pack is lazily loaded and cached, and reloaded when
// the locale changes. Returned pointers are valid until the next locale
// change, i.e. for the duration of one mail build.
const char *mail_i18n_str(const char *key);
