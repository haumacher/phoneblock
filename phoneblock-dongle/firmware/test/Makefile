CC      ?= gcc
CFLAGS  ?= -Wall -Wextra -Werror -O0 -g -I../main

# api_scan depends on cJSON, which ships with ESP-IDF. We compile it
# in via $(IDF_PATH); no toolchain is needed, only the source files.
# Relax warnings on the third-party cJSON.c only.
IDF_PATH ?= $(HOME)/tools/esp/esp-idf
CJSON_DIR := $(IDF_PATH)/components/json/cJSON

BINS = test_sip_parse test_sip_frame test_sip_srv test_tr064_parse test_pairing_parse test_api_scan

test_sip_parse: test_sip_parse.c ../main/sip_parse.c ../main/sip_parse.h \
                ../main/audio.c ../main/audio.h
	$(CC) $(CFLAGS) test_sip_parse.c ../main/sip_parse.c ../main/audio.c -o $@

test_sip_frame: test_sip_frame.c ../main/sip_frame.c ../main/sip_frame.h \
                ../main/sip_parse.c ../main/sip_parse.h
	$(CC) $(CFLAGS) test_sip_frame.c ../main/sip_frame.c ../main/sip_parse.c -o $@

test_sip_srv: test_sip_srv.c ../main/sip_srv.c ../main/sip_srv.h
	$(CC) $(CFLAGS) test_sip_srv.c ../main/sip_srv.c -o $@

test_tr064_parse: test_tr064_parse.c ../main/tr064_parse.c ../main/tr064_parse.h
	$(CC) $(CFLAGS) test_tr064_parse.c ../main/tr064_parse.c -o $@

test_pairing_parse: test_pairing_parse.c ../main/pairing_parse.c ../main/pairing_parse.h
	$(CC) $(CFLAGS) test_pairing_parse.c ../main/pairing_parse.c -o $@

cjson.o: $(CJSON_DIR)/cJSON.c $(CJSON_DIR)/cJSON.h
	$(CC) -O0 -g -I$(CJSON_DIR) -c $< -o $@

test_api_scan: test_api_scan.c ../main/api_scan.c ../main/api_scan.h cjson.o
	$(CC) $(CFLAGS) -I$(CJSON_DIR) test_api_scan.c ../main/api_scan.c cjson.o -o $@

test: $(BINS)
	@set -e; for b in $(BINS); do echo "→ $$b"; ./$$b; done

clean:
	rm -f $(BINS) cjson.o

.PHONY: test clean
