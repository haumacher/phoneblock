#include "heap_guard.h"

// The heap sentinel captures ESP-IDF heap metadata into a device core dump.
// Linux has no equivalent sentinel; breadcrumbs are intentionally discarded.
void heap_guard_note(const char *what)
{
    (void)what;
}
