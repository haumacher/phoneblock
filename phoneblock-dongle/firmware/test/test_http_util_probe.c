#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
// Probe for #532: URL scrubbing and body summaries composed with real-world
// shapes (token in query, IPv6, '@' in path, leading whitespace, XML).
#include "http_util.h"
const char *pb_firmware_version(void){return "t";}
const char *config_device_id(void){return "d";}
const char *pb_platform_tag(void){return "p";}
static int fails;
static void url(const char *in, const char *want){ char o[160]; http_url_for_log(in,o,sizeof o); if(strcmp(o,want)){printf("URL FAIL %s -> %s (want %s)\n",in,o,want);fails++;} }
static void body(const char *in, const char *want){ size_t n=strlen(in); char *c=malloc(n?n:1); memcpy(c,in,n); char o[200]; http_body_summary(c,n,true,o,sizeof o); free(c); if(strcmp(o,want)){printf("BODY FAIL [%s] -> %s (want %s)\n",in,o,want);fails++;} }
int main(void){
  url("https://phoneblock.net/phoneblock/api/num/123?format=json&token=SECRET","https://phoneblock.net/phoneblock/api/num/123");
  url("http://[::1]:8080/pb/api/test#frag","http://[::1]:8080/pb/api/test");
  url("https://u:p@host.example/x","https://host.example/x");
  url("https://host.example/a@b/c?q=1","https://host.example/a@b/c");
  url("https://host.example?token=x","https://host.example");
  body("\r\n  <!DOCTYPE html><html><head><TITLE>502 Bad\n  Gateway</TITLE></head></html>","HTML page \"502 Bad Gateway\"");
  body("<?xml version=\"1.0\"?><error>denied</error>", "HTML page, 42 bytes");
  body("{\"message\":\"<b>x</b>\"}", "\"{\"message\":\"<b>x</b>\"}\"");
  printf("test_http_util_probe: %d tests, %d failures\n", 8, fails); return fails!=0;
}
