CC      ?= gcc
CFLAGS  ?= -Wall -Wextra -Werror -O0 -g -I../main

BIN = test_sip_parse

$(BIN): test_sip_parse.c ../main/sip_parse.c ../main/sip_parse.h
	$(CC) $(CFLAGS) test_sip_parse.c ../main/sip_parse.c -o $(BIN)

test: $(BIN)
	./$(BIN)

clean:
	rm -f $(BIN)

.PHONY: test clean
