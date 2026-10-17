// Host stand-in for the IDF-generated sdkconfig.h, just enough to compile the
// ESP-IDF SPIFFS core (components/spiffs/spiffs/src) and its spiffs_config.h
// on the host for the storage simulation (test_storage_sim.c).
//
// The two settings that shape SPIFFS behaviour on the dongle — the logical
// page size and the garbage-collection budget — are NOT repeated here: the
// Makefile reads them from ../sdkconfig.defaults and passes them with -D, so
// the simulation cannot drift from what the firmware is built with.
// Everything else is the IDF default, as in the dongle's sdkconfig.
#pragma once

#ifndef CONFIG_SPIFFS_PAGE_SIZE
#  error "CONFIG_SPIFFS_PAGE_SIZE missing: the Makefile derives it from sdkconfig.defaults"
#endif
#ifndef CONFIG_SPIFFS_GC_MAX_RUNS
#  error "CONFIG_SPIFFS_GC_MAX_RUNS missing: the Makefile derives it from sdkconfig.defaults"
#endif

#define CONFIG_SPIFFS_CACHE             1
#define CONFIG_SPIFFS_CACHE_WR          1
#define CONFIG_SPIFFS_PAGE_CHECK        1
#define CONFIG_SPIFFS_OBJ_NAME_LEN      32
#define CONFIG_SPIFFS_META_LENGTH       4
#define CONFIG_SPIFFS_USE_MAGIC         1
#define CONFIG_SPIFFS_USE_MAGIC_LENGTH  1
#define CONFIG_SPIFFS_USE_MTIME         1
