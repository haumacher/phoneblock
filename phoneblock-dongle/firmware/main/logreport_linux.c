#include "logreport.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// The log-report beacon (logreport.c) ships the WARN/ERROR lines that the
// ESP-IDF log hook collects in log_capture.c. That hook does not exist on
// Linux yet, so there is nothing to ship; the service's own log goes to the
// journal instead.
void logreport_flush(void)
{
}
