#include "mail.h"

#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// The status mail (mail.c) talks SMTP over ESP-IDF sockets and esp-tls and
// is not ported to Linux yet. These stubs keep the scheduler's mail job and
// the web UI's mail test linkable; the mail job then simply has nothing to
// send.

bool mail_configured(void)
{
    return false;
}

bool mail_send_test(void)
{
    pb_log_warn("mail", "status mail is not supported on Linux yet");
    return false;
}

void mail_daily_flush(void)
{
}

void mail_note_update(const char *version)
{
    (void)version;
}

void mail_report_update(void)
{
}
