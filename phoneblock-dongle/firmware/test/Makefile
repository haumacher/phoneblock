
CC      ?= gcc
CFLAGS  ?= -Wall -Wextra -Werror -O0 -g -I../main -I../core

# api_scan depends on cJSON, which ships with ESP-IDF. We compile it
# in via $(IDF_PATH); no toolchain is needed, only the source files.
# Relax warnings on the third-party cJSON.c only.
IDF_PATH ?= $(HOME)/tools/esp/esp-idf
CJSON_DIR := $(IDF_PATH)/components/json/cJSON

# Linux build of the portable firmware modules (`make test-linux`). It uses
# the distribution cJSON, libsrtp2, libmicrohttpd, libcurl and OpenSSL development packages.
LINUX_BUILD_DIR := ../build/linux-host
LINUX_WEB_DIR := $(LINUX_BUILD_DIR)/web
LINUX_WEB_ASSETS := $(LINUX_WEB_DIR)/index.html.gz \
                    $(LINUX_WEB_DIR)/ab-logo-bot.svg \
                    $(LINUX_WEB_DIR)/ui_en.json
# pkg-config is required: without it the include flags would silently come
# out empty and the build fail later on a missing header.
ifneq ($(filter linux-firmware phoneblock_linux test-linux test_api_linux test_web_http_linux,$(MAKECMDGOALS)),)
ifneq ($(shell pkg-config --exists libcjson libsrtp2 libmicrohttpd libcurl openssl && echo ok),ok)
$(error pkg-config cannot find the Linux build dependencies; install the Build-Depends of phoneblock-dongle/debian/control)
endif
endif
LINUX_PKG_CFLAGS := $(shell pkg-config --cflags libcjson libsrtp2 libmicrohttpd 2>/dev/null)
LINUX_PKG_LIBS := $(shell pkg-config --libs libcjson libsrtp2 libmicrohttpd 2>/dev/null)
LINUX_SRTP_INCLUDEDIR := $(shell pkg-config --variable=includedir libsrtp2 2>/dev/null)
LINUX_NET_LIBS := -lcurl -lssl -lcrypto $(LINUX_PKG_LIBS)
LINUX_CORE_SOURCES := ../core/platform_linux.c ../core/platform_linux_net.c \
                      ../core/sip_parse.c ../core/dial_rules.c ../core/tr064_parse.c
LINUX_MAIN_SOURCES := ../main/announcement.c ../main/api.c \
                      ../main/api_scan.c \
                      ../main/audio.c ../main/blocklist_lookup.c \
                      ../main/blocklist_sync.c ../main/config.c \
                      ../main/firmware_update.c ../main/heap_guard_linux.c \
                      ../main/heap_guard_scan.c \
                      ../main/http_util.c ../main/i18n_sync.c ../main/log_capture.c \
                      ../main/log_capture_linux.c ../main/logreport.c \
                      ../main/mail_linux.c ../main/mail_rcpt.c \
                      ../main/manifest_sig.c ../main/name_filter.c \
                      ../main/net_local.c ../main/phone_norm.c \
                      ../main/report_queue.c ../main/rtp.c \
                      ../main/sched_time.c ../main/scheduler.c \
                      ../main/sip_auth.c ../main/sip_frame.c \
                      ../main/selftest.c ../main/sip_register.c ../main/sip_response.c \
                      ../main/sip_srv.c ../main/sip_transport.c \
                      ../main/stats.c ../main/storage.c \
                      ../main/sync.c ../main/time_sync_linux.c \
                      ../main/time_sync_tz.c ../main/tr064.c \
                      ../main/version_cmp.c \
                      ../main/web_access.c ../main/web_auth.c ../main/web.c \
                      ../main/webhook.c \
                      ../main/web_http_linux.c
LINUX_OBJECTS := $(patsubst ../core/%.c,$(LINUX_BUILD_DIR)/core/%.o,$(LINUX_CORE_SOURCES)) \
                 $(patsubst ../main/%.c,$(LINUX_BUILD_DIR)/main/%.o,$(LINUX_MAIN_SOURCES))
# -DSTORAGE_HOSTFS selects storage.c's plain-filesystem backend.
LINUX_CPPFLAGS := -DSTORAGE_HOSTFS -I../main -I../core $(LINUX_PKG_CFLAGS) \
                  -I$(LINUX_SRTP_INCLUDEDIR)/srtp2 $(CPPFLAGS)
# The version reported in the User-Agent (pb_firmware_version()): the firmware
# version these modules were built from, derived exactly like the ESP32 build
# does it (firmware/CMakeLists.txt), so the server sees one version scheme
# for the whole fleet. Outside a git checkout the caller passes it (debian/
# rules falls back to the package version); without either it is "linux-dev".
ifeq ($(origin PHONEBLOCK_VERSION),undefined)
PHONEBLOCK_VERSION := $(shell git describe --tags --match "dongle-v*" --always --dirty 2>/dev/null | sed 's/^dongle-v//')
endif
ifneq ($(PHONEBLOCK_VERSION),)
LINUX_CPPFLAGS += -DPHONEBLOCK_VERSION='"$(PHONEBLOCK_VERSION)"'
endif
ifneq ($(strip $(PHONEBLOCK_DATA_DIR)),)
LINUX_CPPFLAGS += -DPHONEBLOCK_DATA_DIR=\"$(PHONEBLOCK_DATA_DIR)\"
endif

BINS = test_strbuf test_sip_parse test_sip_frame test_sip_srv test_sip_auth test_sip_response test_tr064_parse test_phone_norm test_api_scan test_log_capture test_improv_proto test_blocklist_lookup test_sched_time test_smtp_body test_mail_html test_mail_rcpt test_name_filter test_version_cmp test_http_util test_net_local test_heap_guard test_platform_linux test_log_capture_linux test_time_sync_tz test_ota_manifest test_stats test_display_model test_display_probe test_storage_budget test_storage_sim test_storage_hostfs

test_sip_parse: test_sip_parse.c ../core/sip_parse.c ../core/sip_parse.h \
                ../core/dial_rules.c ../core/dial_rules.h \
                ../main/audio.c ../main/audio.h
	$(CC) $(CFLAGS) test_sip_parse.c ../core/sip_parse.c ../core/dial_rules.c ../main/audio.c -o $@

test_sip_frame: test_sip_frame.c ../main/sip_frame.c ../main/sip_frame.h \
                ../core/sip_parse.c ../core/sip_parse.h ../core/dial_rules.c
	$(CC) $(CFLAGS) test_sip_frame.c ../main/sip_frame.c ../core/sip_parse.c ../core/dial_rules.c -o $@

test_sip_srv: test_sip_srv.c ../main/sip_srv.c ../main/sip_srv.h
	$(CC) $(CFLAGS) test_sip_srv.c ../main/sip_srv.c -o $@

test_sip_auth: test_sip_auth.c ../main/sip_auth.c ../main/sip_auth.h
	$(CC) $(CFLAGS) test_sip_auth.c ../main/sip_auth.c -o $@

test_tr064_parse: test_tr064_parse.c ../core/tr064_parse.c ../core/tr064_parse.h
	$(CC) $(CFLAGS) -I../core test_tr064_parse.c ../core/tr064_parse.c -o $@

test_phone_norm: test_phone_norm.c ../main/phone_norm.c ../main/phone_norm.h \
                 ../core/dial_rules.c ../core/dial_rules.h
	$(CC) $(CFLAGS) test_phone_norm.c ../main/phone_norm.c ../core/dial_rules.c -o $@

test_log_capture: test_log_capture.c ../main/log_capture.c ../main/log_capture.h
	$(CC) $(CFLAGS) test_log_capture.c ../main/log_capture.c -o $@

test_improv_proto: test_improv_proto.c ../main/improv_proto.c ../main/improv_proto.h
	$(CC) $(CFLAGS) test_improv_proto.c ../main/improv_proto.c -o $@

cjson.o: $(CJSON_DIR)/cJSON.c $(CJSON_DIR)/cJSON.h
	$(CC) -O0 -g -I$(CJSON_DIR) -c $< -o $@

test_api_scan: test_api_scan.c ../main/api_scan.c ../main/api_scan.h cjson.o
	$(CC) $(CFLAGS) -I$(CJSON_DIR) test_api_scan.c ../main/api_scan.c cjson.o -o $@

test_ota_manifest: test_ota_manifest.c ../main/ota_manifest.c ../main/ota_manifest.h cjson.o
	$(CC) $(CFLAGS) -I$(CJSON_DIR) test_ota_manifest.c ../main/ota_manifest.c cjson.o -o $@

test_storage_budget: test_storage_budget.c ../main/storage_budget.c ../main/storage_budget.h
	$(CC) $(CFLAGS) test_storage_budget.c ../main/storage_budget.c -o $@

# test_storage_sim runs the unmodified ESP-IDF SPIFFS core on a RAM flash
# (spiffs_host/) with the dongle's geometry, read from the same files the
# firmware is built from: page size and GC budget from sdkconfig.defaults,
# the partition size from partitions.csv. The core is third-party code, built
# once with warnings off; storage.c / storage_budget.c are built with ours.
SPIFFS_SRC := $(IDF_PATH)/components/spiffs/spiffs/src
SPIFFS_INC := $(IDF_PATH)/components/spiffs/include
SIM_PAGE   := $(shell sed -n 's/^CONFIG_SPIFFS_PAGE_SIZE=//p' ../sdkconfig.defaults)
SIM_GCRUNS := $(shell sed -n 's/^CONFIG_SPIFFS_GC_MAX_RUNS=//p' ../sdkconfig.defaults)
SIM_PART   := $(shell awk -F, '$$1 ~ /^storage/ { gsub(/ /, "", $$5); print $$5 }' ../partitions.csv)
SIM_FLAGS  := -DCONFIG_SPIFFS_PAGE_SIZE=$(SIM_PAGE) -DCONFIG_SPIFFS_GC_MAX_RUNS=$(SIM_GCRUNS) \
              -DSIM_PART_SIZE=$(SIM_PART) -Ispiffs_host -I$(SPIFFS_INC) -I$(SPIFFS_SRC)
SPIFFS_CORE := spiffs_core_cache.o spiffs_core_check.o spiffs_core_gc.o \
               spiffs_core_hydrogen.o spiffs_core_nucleus.o

spiffs_core_%.o: $(SPIFFS_SRC)/spiffs_%.c spiffs_host/sdkconfig.h ../sdkconfig.defaults ../partitions.csv
	$(CC) -O2 -g -w $(SIM_FLAGS) -c $< -o $@

test_storage_sim: test_storage_sim.c spiffs_host/spiffs_sim.c spiffs_host/spiffs_sim.h \
                  ../main/storage.c ../main/storage.h ../main/storage_budget.c \
                  ../main/storage_budget.h $(SPIFFS_CORE)
	$(CC) $(CFLAGS) $(SIM_FLAGS) -DSTORAGE_UNLINK=sim_vfs_unlink \
	    test_storage_sim.c spiffs_host/spiffs_sim.c ../main/storage.c \
	    ../main/storage_budget.c $(SPIFFS_CORE) -o $@

test_blocklist_lookup: test_blocklist_lookup.c ../main/blocklist_lookup.c ../main/blocklist_lookup.h
	$(CC) $(CFLAGS) test_blocklist_lookup.c ../main/blocklist_lookup.c -o $@

test_sched_time: test_sched_time.c ../main/sched_time.c ../main/sched_time.h
	$(CC) $(CFLAGS) test_sched_time.c ../main/sched_time.c -o $@

test_smtp_body: test_smtp_body.c ../main/smtp_body.c ../main/smtp_body.h
	$(CC) $(CFLAGS) test_smtp_body.c ../main/smtp_body.c -o $@

test_mail_html: test_mail_html.c ../main/mail_html.c ../main/mail_html.h
	$(CC) $(CFLAGS) test_mail_html.c ../main/mail_html.c -o $@

test_mail_rcpt: test_mail_rcpt.c ../main/mail_rcpt.c ../main/mail_rcpt.h ../main/strbuf.h
	$(CC) $(CFLAGS) test_mail_rcpt.c ../main/mail_rcpt.c -o $@

test_name_filter: test_name_filter.c ../main/name_filter.c ../main/name_filter.h
	$(CC) $(CFLAGS) test_name_filter.c ../main/name_filter.c -o $@

test_version_cmp: test_version_cmp.c ../main/version_cmp.c ../main/version_cmp.h
	$(CC) $(CFLAGS) test_version_cmp.c ../main/version_cmp.c -o $@

test_http_util: test_http_util.c linux_test_util.h ../main/http_util.c \
                ../main/http_util.h
	$(CC) $(CFLAGS) test_http_util.c ../main/http_util.c -o $@

test_net_local: test_net_local.c ../main/net_local.c ../main/net_local.h
	$(CC) $(CFLAGS) test_net_local.c ../main/net_local.c -o $@

test_heap_guard: test_heap_guard.c ../main/heap_guard_scan.c ../main/heap_guard_scan.h
	$(CC) $(CFLAGS) test_heap_guard.c ../main/heap_guard_scan.c -o $@

# test_stats and test_platform_linux link only core/platform_linux.c (libc +
# pthreads); the libcurl/OpenSSL half lives in platform_linux_net.c.
test_stats: test_stats.c ../main/stats.c ../main/stats.h ../main/api.h ../main/display.h \
            ../core/platform_linux.c ../core/platform.h ../main/log_capture.c
	$(CC) $(CFLAGS) -pthread test_stats.c ../main/stats.c ../core/platform_linux.c ../main/log_capture.c -o $@

# The display's model (badge rule, caller screen, UTF-8, text fitting) on top
# of the real stats ring and the generated fonts. With AddressSanitizer: the
# UTF-8 decoder and the text fitting must never overrun, whatever the input.
test_display_model: test_display_model.c ../main/display_model.c ../main/display_model.h \
                    ../main/lcd_font.c ../main/lcd_font.h ../main/strbuf.h \
                    ../main/stats.c ../main/stats.h ../main/display.h ../main/api.h \
                    ../core/platform_linux.c ../core/platform.h ../main/log_capture.c
	$(CC) $(CFLAGS) -fsanitize=address -pthread test_display_model.c ../main/display_model.c \
	  ../main/lcd_font.c ../main/stats.c ../core/platform_linux.c ../main/log_capture.c -o $@

test_display_probe: test_display_probe.c ../main/display_model.c ../main/display_model.h \
                    ../main/lcd_font.c ../main/lcd_font.h ../main/strbuf.h \
                    ../main/stats.c ../main/stats.h ../main/display.h ../main/api.h \
                    ../core/platform_linux.c ../core/platform.h ../main/log_capture.c
	$(CC) $(CFLAGS) -fsanitize=address -pthread test_display_probe.c ../main/display_model.c \
	  ../main/lcd_font.c ../main/stats.c ../core/platform_linux.c ../main/log_capture.c -o $@

test_platform_linux: test_platform_linux.c linux_test_util.h \
                     ../core/platform_linux.c ../core/platform.h \
                     ../main/config.c ../main/config.h \
                     ../main/log_capture.c ../main/log_capture.h
	$(CC) $(CFLAGS) -pthread test_platform_linux.c ../core/platform_linux.c ../main/config.c ../main/log_capture.c -o $@

test_time_sync_tz: test_time_sync_tz.c linux_test_util.h ../main/time_sync_tz.c \
                   ../main/time_sync.h ../core/platform_linux.c ../core/platform.h
	$(CC) $(CFLAGS) -pthread test_time_sync_tz.c ../main/time_sync_tz.c \
	  ../core/platform_linux.c -o $@

test_log_capture_linux: test_log_capture_linux.c linux_test_util.h \
                        ../main/log_capture_linux.c ../main/log_capture.c \
                        ../main/log_capture.h ../main/stats.c ../main/stats.h \
                        ../main/config.c ../main/config.h \
                        ../core/platform_linux.c ../core/platform.h
	$(CC) $(CFLAGS) -pthread test_log_capture_linux.c ../main/log_capture_linux.c \
	  ../main/log_capture.c ../main/stats.c ../main/config.c \
	  ../core/platform_linux.c -o $@

# The hostfs backend of storage.c. The data dir is a fresh mkdtemp()
# directory the test publishes in PB_HOSTFS_TEST_DIR.
test_storage_hostfs: test_storage_hostfs.c linux_test_util.h \
                     ../main/storage.c ../main/storage.h \
                     ../core/platform_linux.c ../core/platform.h \
                     ../main/log_capture.c ../main/log_capture.h
	$(CC) $(CFLAGS) -DSTORAGE_HOSTFS \
	  -DPHONEBLOCK_DATA_DIR='getenv("PB_HOSTFS_TEST_DIR")' -pthread \
	  test_storage_hostfs.c ../main/storage.c ../core/platform_linux.c \
	  ../main/log_capture.c -o $@

test_platform_linux_net: test_platform_linux_net.c linux_test_util.h \
                         ../core/platform_linux.c ../core/platform_linux_net.c \
                         ../core/platform.h ../main/log_capture.c
	$(CC) $(CFLAGS) -pthread test_platform_linux_net.c ../core/platform_linux.c ../core/platform_linux_net.c ../main/log_capture.c $(LINUX_NET_LIBS) -o $@

$(LINUX_BUILD_DIR)/core/%.o: ../core/%.c
	mkdir -p $(@D)
	$(CC) $(CFLAGS) -std=gnu11 $(LINUX_CPPFLAGS) -c $< -o $@

$(LINUX_BUILD_DIR)/main/%.o: ../main/%.c
	mkdir -p $(@D)
	$(CC) $(CFLAGS) -std=gnu11 $(LINUX_CPPFLAGS) -c $< -o $@

$(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a: $(LINUX_OBJECTS)
	$(AR) rcs $@ $^

linux-firmware: $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a

test_api_linux: test_api_linux.c linux_test_util.h \
                $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a
	$(CC) $(CFLAGS) -pthread $(LINUX_PKG_CFLAGS) $< \
	  $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a \
	  $(LINUX_NET_LIBS) -o $@

test_web_calls_linux: test_web_calls_linux.c linux_test_util.h \
                      $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a
	$(CC) $(CFLAGS) -pthread $(LINUX_PKG_CFLAGS) $< \
	  $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a \
	  $(LINUX_NET_LIBS) -o $@

test_webhook_linux: test_webhook_linux.c linux_test_util.h \
                    $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a
	$(CC) $(CFLAGS) -pthread $(LINUX_PKG_CFLAGS) $< \
	  $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a \
	  $(LINUX_NET_LIBS) -o $@

test_web_http_linux: test_web_http_linux.c linux_test_util.h ../main/web_http.h \
                     ../main/web_http_linux.c
	$(CC) $(CFLAGS) -pthread $(shell pkg-config --cflags libmicrohttpd) \
	  test_web_http_linux.c ../main/web_http_linux.c \
	  $(shell pkg-config --libs libmicrohttpd) -o $@

# strbuf.h is header-only; build its test with AddressSanitizer so any overrun
# of the (malloc'd) destination buffer aborts.
test_strbuf: test_strbuf.c ../main/strbuf.h
	$(CC) $(CFLAGS) -fsanitize=address test_strbuf.c -o $@

# Built with AddressSanitizer: this test exists to catch buffer overruns in
# the SIP response builder, so the sanitizer must be active for it to mean
# anything. sip_response.c / the test define _GNU_SOURCE themselves (memmem).
test_sip_response: test_sip_response.c ../main/sip_response.c ../main/sip_response.h \
                   ../core/sip_parse.c ../core/sip_parse.h ../main/audio.c \
                   ../core/dial_rules.c
	$(CC) $(CFLAGS) -fsanitize=address test_sip_response.c ../main/sip_response.c ../core/sip_parse.c ../core/dial_rules.c ../main/audio.c -o $@

test: $(BINS)
	@set -e; for b in $(BINS); do echo "→ $$b"; ./$$b; done
	@echo "→ test-signing-roundtrip.sh"; ./test-signing-roundtrip.sh

# Linux build of the firmware modules plus its network tests; needs the
# extra prerequisites listed at LINUX_BUILD_DIR above.
LINUX_BINS = test_platform_linux_net test_api_linux test_web_http_linux test_web_calls_linux test_webhook_linux

# The service binary. debian/rules builds it with the distribution's CFLAGS,
# CPPFLAGS and LDFLAGS (optimisation and hardening) instead of the test flags.
$(LINUX_BUILD_DIR)/phoneblock-dongle: ../main/main_linux.c \
                                      $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a
	$(CC) $(CFLAGS) -std=gnu11 -pthread $(LINUX_CPPFLAGS) $< \
	  $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a \
	  $(LDFLAGS) $(LINUX_NET_LIBS) -o $@

$(LINUX_WEB_DIR)/index.html.gz: ../main/web/index.html
	mkdir -p $(@D)
	gzip -9 -n -c $< > $@

$(LINUX_WEB_DIR)/ab-logo-bot.svg: ../main/web/ab-logo-bot.svg
	mkdir -p $(@D)
	install -m 0644 $< $@

$(LINUX_WEB_DIR)/ui_en.json: ../i18n/l10n/ui/ui_en.arb
	mkdir -p $(@D)
	python3 -c 'import json,sys; d=json.load(open(sys.argv[1],encoding="utf-8")); json.dump({k:v for k,v in d.items() if k=="@@locale" or not k.startswith("@")},open(sys.argv[2],"w",encoding="utf-8"),ensure_ascii=False,separators=(",",":"))' $< $@

phoneblock_linux: $(LINUX_BUILD_DIR)/phoneblock-dongle $(LINUX_WEB_ASSETS)

test-linux: linux-firmware phoneblock_linux $(LINUX_BINS)
	@set -e; for b in $(LINUX_BINS); do echo "→ $$b"; ./$$b; done
	@echo "→ test_linux_entrypoint.sh"; ./test_linux_entrypoint.sh
	@echo "→ test_deb_version.sh"; ./test_deb_version.sh

clean:
	rm -f $(BINS) $(LINUX_BINS) cjson.o $(SPIFFS_CORE) $(LINUX_OBJECTS) $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a $(LINUX_BUILD_DIR)/phoneblock-dongle

.PHONY: linux-firmware phoneblock_linux test test-linux clean
