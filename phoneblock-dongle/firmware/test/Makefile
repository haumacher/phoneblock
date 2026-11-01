
CC      ?= gcc
CFLAGS  ?= -Wall -Wextra -Werror -O0 -g -I../main -I../core

# api_scan depends on cJSON, which ships with ESP-IDF. We compile it
# in via $(IDF_PATH); no toolchain is needed, only the source files.
# Relax warnings on the third-party cJSON.c only.
IDF_PATH ?= $(HOME)/tools/esp/esp-idf
CJSON_DIR := $(IDF_PATH)/components/json/cJSON

BINS = test_strbuf test_sip_parse test_sip_frame test_sip_srv test_sip_auth test_sip_response test_tr064_parse test_phone_norm test_api_scan test_log_capture test_improv_proto test_blocklist_lookup test_sched_time test_smtp_body test_mail_html test_mail_calls test_mail_rcpt test_name_filter test_version_cmp test_http_util test_net_local test_heap_guard test_platform_linux test_log_capture_linux test_time_sync_tz test_ota_manifest test_stats test_display_model test_display_probe test_storage_budget test_storage_sim test_storage_hostfs test_vad test_dialog test_dialog_probe test_audio_manifest test_link_proto

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

# The device-code link's server answers and state machine (#621). With
# AddressSanitizer: the parsers copy server-chosen strings into fixed fields.
test_link_proto: test_link_proto.c ../main/link_proto.c ../main/link_proto.h cjson.o
	$(CC) $(CFLAGS) -fsanitize=address -I$(CJSON_DIR) test_link_proto.c \
	  ../main/link_proto.c cjson.o -o $@

# The dialog's receive path: A-law decode and the VAD (needs libm).
test_vad: test_vad.c ../main/vad.c ../main/vad.h ../main/audio.c ../main/audio.h
	$(CC) $(CFLAGS) test_vad.c ../main/vad.c ../main/audio.c -lm -o $@

# The dialog state machine, its clip-set index, the manifest selection of a
# clip set (cJSON) and the RTP receive parser.
# The clip store runs against a fresh temp directory (PB_DIALOG_TEST_DIR,
# set by main()), like test_storage_hostfs.
test_dialog: test_dialog.c ../main/dialog.c ../main/dialog.h \
             ../main/dialog_manifest.c ../main/dialog_manifest.h \
             ../main/audio_manifest.c ../main/audio_manifest.h \
             ../main/dialog_store.c ../main/dialog_store.h \
             ../main/rtp_rx.c ../main/rtp_rx.h ../main/vad.h ../main/strbuf.h cjson.o
	$(CC) $(CFLAGS) -fsanitize=address -I$(CJSON_DIR) \
	  -DPHONEBLOCK_DATA_DIR='getenv("PB_DIALOG_TEST_DIR")' \
	  test_dialog.c ../main/dialog.c ../main/dialog_manifest.c ../main/audio_manifest.c \
	  ../main/dialog_store.c ../main/rtp_rx.c cjson.o -o $@

test_dialog_probe: test_dialog_probe.c ../main/dialog.c ../main/dialog.h \
             ../main/dialog_manifest.c ../main/dialog_manifest.h \
             ../main/audio_manifest.c ../main/audio_manifest.h ../main/vad.h \
             ../main/storage_budget.c ../main/storage_budget.h cjson.o
	$(CC) $(CFLAGS) -fsanitize=address -I$(CJSON_DIR) test_dialog_probe.c ../main/dialog.c \
	  ../main/dialog_manifest.c ../main/audio_manifest.c ../main/storage_budget.c cjson.o -o $@

# The audio channel's manifest (audio_manifest.c) and the dialog tiers read
# from it (dialog_manifest.c).
test_audio_manifest: test_audio_manifest.c ../main/audio_manifest.c ../main/audio_manifest.h \
             ../main/dialog_manifest.c ../main/dialog_manifest.h ../main/dialog.c cjson.o
	$(CC) $(CFLAGS) -fsanitize=address -I$(CJSON_DIR) test_audio_manifest.c \
	  ../main/audio_manifest.c ../main/dialog_manifest.c ../main/dialog.c cjson.o -o $@

# The storage partition's size in a partition table (column 5 of its line).
part_size = $(shell awk -F, '$$1 ~ /^storage/ { gsub(/ /, "", $$5); print $$5 }' $(1))

test_storage_budget: test_storage_budget.c ../main/storage_budget.c ../main/storage_budget.h \
                     ../partitions.csv ../partitions-c3.csv ../partitions-16mb.csv
	$(CC) $(CFLAGS) -DPART_CLASSIC=$(call part_size,../partitions.csv) \
	  -DPART_C3=$(call part_size,../partitions-c3.csv) \
	  -DPART_S3=$(call part_size,../partitions-16mb.csv) \
	  test_storage_budget.c ../main/storage_budget.c -o $@

# test_storage_sim runs storage.c's LittleFS backend against the littlefs
# core the firmware links — the joltwallet/littlefs component, fetched into
# ../managed_components by the component manager (any idf.py build or
# reconfigure) — on a RAM flash (lfs_host/) with the partition size of
# partitions.csv. The core is third-party code, built once with warnings off;
# storage.c / storage_budget.c are built with ours.
LFS_DIR   := ../managed_components/joltwallet__littlefs/src/littlefs
SIM_PART  := $(call part_size,../partitions.csv)
SIM_FLAGS := -DSIM_PART_SIZE=$(SIM_PART) -Ilfs_host -I$(LFS_DIR)
LFS_CORE  := lfs_core_lfs.o lfs_core_lfs_util.o

$(LFS_DIR)/lfs.c $(LFS_DIR)/lfs_util.c:
	@echo "$@ missing: the littlefs sources come with the managed components;" >&2
	@echo "run 'idf.py reconfigure' (or any idf.py build) in phoneblock-dongle/firmware once." >&2
	@exit 1

lfs_core_%.o: $(LFS_DIR)/%.c lfs_host/lfs_sim_defs.h
	$(CC) -O2 -g -w -Ilfs_host -DLFS_DEFINES=lfs_sim_defs.h -c $< -o $@

test_storage_sim: test_storage_sim.c lfs_host/lfs_sim.c lfs_host/lfs_sim.h \
                  lfs_host/esp_littlefs.h lfs_host/esp_partition.h \
                  ../main/storage.c ../main/storage.h ../main/storage_budget.c \
                  ../main/storage_budget.h ../partitions.csv $(LFS_CORE)
	$(CC) $(CFLAGS) $(SIM_FLAGS) -DSTORAGE_UNLINK=sim_vfs_unlink \
	    test_storage_sim.c lfs_host/lfs_sim.c ../main/storage.c \
	    ../main/storage_budget.c $(LFS_CORE) -o $@

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
test_stats: test_stats.c ../main/stats.c ../main/stats.h ../main/api.h ../main/api_fail.c \
            ../main/display.h ../core/platform_linux.c ../core/platform.h ../main/log_capture.c
	$(CC) $(CFLAGS) -pthread test_stats.c ../main/stats.c ../main/api_fail.c ../core/platform_linux.c ../main/log_capture.c -o $@

# Which calls the status mail reports (issue #556): all, or only blocked ones.
test_mail_calls: test_mail_calls.c ../main/mail_calls.c ../main/mail_calls.h ../main/stats.h
	$(CC) $(CFLAGS) test_mail_calls.c ../main/mail_calls.c -o $@

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
                     ../main/config.c ../main/config.h ../main/dialog.c ../main/dialog.h \
                     ../main/log_capture.c ../main/log_capture.h
	$(CC) $(CFLAGS) -pthread test_platform_linux.c ../core/platform_linux.c ../main/config.c \
	  ../main/dialog.c ../main/log_capture.c -o $@

test_time_sync_tz: test_time_sync_tz.c linux_test_util.h ../main/time_sync_tz.c \
                   ../main/time_sync.h ../core/platform_linux.c ../core/platform.h
	$(CC) $(CFLAGS) -pthread test_time_sync_tz.c ../main/time_sync_tz.c \
	  ../core/platform_linux.c -o $@

test_log_capture_linux: test_log_capture_linux.c linux_test_util.h \
                        ../main/log_capture_linux.c ../main/log_capture.c \
                        ../main/log_capture.h ../main/stats.c ../main/stats.h \
                        ../main/config.c ../main/config.h ../main/dialog.c \
                        ../core/platform_linux.c ../core/platform.h
	$(CC) $(CFLAGS) -pthread test_log_capture_linux.c ../main/log_capture_linux.c \
	  ../main/log_capture.c ../main/stats.c ../main/config.c ../main/dialog.c \
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

# The library of the firmware modules that the Linux tests link, and the
# service itself, come from ../linux/Makefile, shared with debian/rules
# (#624). Its dependency check applies to these goals.
LINUX_GOALS := linux-firmware phoneblock_linux test-linux test_api_linux test_link_linux test_web_http_linux test_web_calls_linux test_webhook_linux test_web_dialog_linux
include ../linux/Makefile

test_api_linux: test_api_linux.c linux_test_util.h \
                $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a
	$(CC) $(CFLAGS) -pthread $(LINUX_PKG_CFLAGS) $< \
	  $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a \
	  $(LINUX_NET_LIBS) -o $@

# The device-code link (#621): the real web server and poll task against a
# fake of phoneblock.net's /api/link endpoints.
test_link_linux: test_link_linux.c linux_test_util.h \
                 $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a
	$(CC) $(CFLAGS) -pthread $(LINUX_PKG_CFLAGS) $< \
	  $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a \
	  $(LINUX_NET_LIBS) -o $@

test_web_calls_linux: test_web_calls_linux.c linux_test_util.h \
                      $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a
	$(CC) $(CFLAGS) -pthread $(LINUX_PKG_CFLAGS) $< \
	  $(LINUX_BUILD_DIR)/libphoneblock_firmware_linux.a \
	  $(LINUX_NET_LIBS) -o $@

test_web_dialog_linux: test_web_dialog_linux.c linux_test_util.h \
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
	@# The web UI's dialog set builder against test_dialog.c's vector (needs node).
	@if command -v node >/dev/null; then echo "→ dialog_set_js_check.js"; node dialog_set_js_check.js; \
	 else echo "→ dialog_set_js_check.js skipped (no node)"; fi

# Linux build of the firmware modules plus its network tests; needs the
# extra prerequisites listed in ../linux/Makefile.
LINUX_BINS = test_platform_linux_net test_api_linux test_link_linux test_web_http_linux test_web_calls_linux test_webhook_linux test_web_dialog_linux

test-linux: linux-firmware phoneblock_linux $(LINUX_BINS)
	@set -e; for b in $(LINUX_BINS); do echo "→ $$b"; ./$$b; done
	@echo "→ test_linux_entrypoint.sh"; ./test_linux_entrypoint.sh
	@echo "→ test_deb_version.sh"; ./test_deb_version.sh

# The Linux build products are removed by the clean:: rule of ../linux/Makefile.
clean::
	rm -f $(BINS) $(LINUX_BINS) cjson.o $(LFS_CORE) spiffs_core_*.o

.PHONY: linux-firmware phoneblock_linux test test-linux clean
