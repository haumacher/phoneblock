CC      ?= gcc
CFLAGS  ?= -Wall -Wextra -Werror -O0 -g -I../main

BINS = test_sip_parse test_tr064_parse

test_sip_parse: test_sip_parse.c ../main/sip_parse.c ../main/sip_parse.h \
                ../main/audio.c ../main/audio.h
	$(CC) $(CFLAGS) test_sip_parse.c ../main/sip_parse.c ../main/audio.c -o $@

test_tr064_parse: test_tr064_parse.c ../main/tr064_parse.c ../main/tr064_parse.h
	$(CC) $(CFLAGS) test_tr064_parse.c ../main/tr064_parse.c -o $@

test: $(BINS)
	@set -e; for b in $(BINS); do echo "→ $$b"; ./$$b; done

clean:
	rm -f $(BINS)

.PHONY: test clean
