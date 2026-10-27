// Host test for http_util.c, the User-Agent the server parses: the device
// id must stay the last bracket (DongleRecognizer.DEVICE_ID, the
// REGEXP_REPLACE in DiagnosticsMapper), with the platform comment before
// it. http_util_user_agent() caches its string, so every case runs in its
// own child process.
#include <stdio.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

#include "config.h"
#include "http_util.h"
#include "linux_test_util.h"
#include "platform.h"

// Stubs for what http_util.c reads from the config and platform layers.
static const char *s_version;
static const char *s_platform;
static const char *s_device_id;

const char *pb_firmware_version(void) { return s_version; }
const char *pb_platform_tag(void) { return s_platform; }
const char *config_device_id(void) { return s_device_id; }

#define ID "01234567-89ab-cdef-0123-456789abcdef"

static void expect(const char *version, const char *platform,
                   const char *device_id, const char *expected)
{
    pid_t pid = fork();
    REQUIRE(pid >= 0);
    if (pid == 0) {
        s_version = version;
        s_platform = platform;
        s_device_id = device_id;
        const char *agent = http_util_user_agent();
        if (strcmp(agent, expected) != 0) {
            fprintf(stderr, "FAIL: got \"%s\", expected \"%s\"\n",
                    agent, expected);
            _exit(1);
        }
        _exit(0);
    }
    int status;
    REQUIRE(waitpid(pid, &status, 0) == pid);
    REQUIRE(WIFEXITED(status) && WEXITSTATUS(status) == 0);
}

int main(void)
{
    // ESP32: no platform comment, the string stays as it was.
    expect("1.7.0", NULL, ID, "PhoneBlock-Dongle/1.7.0 (" ID ")");
    expect("1.7.0", "", ID, "PhoneBlock-Dongle/1.7.0 (" ID ")");

    // Linux: the platform comment goes before the device id.
    expect("1.7.0-beta4-12-g092776f", "Linux", ID,
           "PhoneBlock-Dongle/1.7.0-beta4-12-g092776f (Linux) (" ID ")");
    expect("1.7.0-beta4-12-g092776f", "Linux; HA", ID,
           "PhoneBlock-Dongle/1.7.0-beta4-12-g092776f (Linux; HA) (" ID ")");

    // No device id: no platform comment either, or the server would read it
    // as the device id.
    expect("1.7.0", "Linux", "", "PhoneBlock-Dongle/1.7.0");
    expect("1.7.0", "Linux", NULL, "PhoneBlock-Dongle/1.7.0");

    // Missing version.
    expect(NULL, "Linux", ID, "PhoneBlock-Dongle/0.0.0 (Linux) (" ID ")");

    // Too long for the buffer with the platform comment: the comment is
    // dropped instead of cutting off the device id. With this 41-character
    // version the string is 98 bytes without the comment, 110 with
    // "Linux; HA" and 122 with "Linux; Home Assistant"; the buffer holds
    // 119 plus the NUL.
    const char *long_version = "1.7.0-beta4-12345-g0123456789abcdef-dirty";
    char expected[128];
    snprintf(expected, sizeof(expected),
             "PhoneBlock-Dongle/%s (Linux; HA) (" ID ")", long_version);
    expect(long_version, "Linux; HA", ID, expected);
    snprintf(expected, sizeof(expected),
             "PhoneBlock-Dongle/%s (" ID ")", long_version);
    expect(long_version, "Linux; Home Assistant", ID, expected);

    printf("test_http_util: all passed\n");
    return 0;
}
