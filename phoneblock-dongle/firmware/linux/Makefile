# Build of the dongle firmware's Linux service: the portable firmware modules
# as a static library, the phoneblock-dongle binary and its web assets, all in
# ../build/linux-host (#624). debian/rules builds the package from here with
# the distribution's flags; test/Makefile includes this file, so the Linux
# tests link the same library.
#
#   make -C phoneblock-dongle/firmware/linux          library, binary, web assets
#   make -C phoneblock-dongle/firmware/linux clean
#
# Paths are relative to this directory. firmware/test is its sibling, so they
# resolve the same when test/Makefile includes this file.

CC     ?= gcc
CFLAGS ?= -Wall -Wextra -O2 -g

# Goals that need the build dependencies (checked below); test/Makefile sets
# its own list before including this file.
LINUX_GOALS ?= all linux-firmware phoneblock_linux

# Linux build of the portable firmware modules (`make test-linux`). It uses
# the distribution cJSON, libsrtp2, libmicrohttpd, libcurl and OpenSSL development packages.
LINUX_BUILD_DIR := ../build/linux-host
LINUX_WEB_DIR := $(LINUX_BUILD_DIR)/web
LINUX_WEB_ASSETS := $(LINUX_WEB_DIR)/index.html.gz \
                    $(LINUX_WEB_DIR)/ab-logo-bot.svg \
                    $(LINUX_WEB_DIR)/ui_en.json
# pkg-config is required: without it the include flags would silently come
# out empty and the build fail later on a missing header.
ifneq ($(filter $(LINUX_GOALS),$(or $(MAKECMDGOALS),all)),)
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

all: phoneblock_linux

$(LINUX_BUILD_DIR)/core/%.o: ../core/%.c
	mkdir -p $(@D)
	$(CC) $(CFLAGS) -std=gnu11 $(LINUX_CPPFLAGS) -c $< -o $@

$(LINUX_BUILD_DIR)/main/%.o: ../main/%.c
	mkdir -p $(@D)
	$(CC) $(CFLAGS) -std=gnu11 $(LINUX_CPPFLAGS) -c $< -o $@

$(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a: $(LINUX_OBJECTS)
	$(AR) rcs $@ $^

linux-firmware: $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a

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

clean::
	rm -f $(LINUX_OBJECTS) $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a $(LINUX_BUILD_DIR)/phoneblock-dongle

.PHONY: all linux-firmware phoneblock_linux clean
