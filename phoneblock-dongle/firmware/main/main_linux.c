#define _POSIX_C_SOURCE 200809L

#include "announcement.h"
#include "api.h"
#include "config.h"
#include "platform.h"
#include "report_queue.h"
#include "scheduler.h"
#include "sip_register.h"
#include "sip_transport.h"
#include "stats.h"

#include <errno.h>
#include <getopt.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void print_usage(const char *program)
{
    fprintf(stderr,
            "Usage: %s [--config PATH] [--check-config | --probe-sip]\n",
            program);
}

static bool check_config(void)
{
    bool valid = true;
    if (!config_sip_host()[0]) {
        pb_log_err("linux", "SIP host is not configured");
        valid = false;
    }
    if (!config_sip_user()[0]) {
        pb_log_err("linux", "SIP user is not configured");
        valid = false;
    }
    if (!config_phoneblock_token()[0]) {
        pb_log_err("linux", "PhoneBlock token is not configured");
        valid = false;
    }
    if (valid) pb_log_info("linux", "configuration is ready");
    return valid;
}

static int probe_sip(void)
{
    const char *host = config_sip_host();
    if (!host[0]) {
        pb_log_err("linux", "SIP host is not configured");
        return EXIT_FAILURE;
    }

    sip_transport_t *transport = sip_transport_open(
        config_sip_transport(), host, config_sip_port(), host,
        config_sip_local_port());
    if (!transport) return EXIT_FAILURE;

    pb_log_info("linux", "SIP transport ready at %s:%d",
                sip_transport_local_ip(transport),
                sip_transport_local_port(transport));
    sip_transport_close(transport);
    return EXIT_SUCCESS;
}

int main(int argc, char **argv)
{
    const char *config_path = NULL;
    bool check = false;
    bool probe = false;
    static const struct option options[] = {
        { "config", required_argument, NULL, 'c' },
        { "check-config", no_argument, NULL, 't' },
        { "probe-sip", no_argument, NULL, 'p' },
        { "help", no_argument, NULL, 'h' },
        { NULL, 0, NULL, 0 },
    };

    int option;
    while ((option = getopt_long(argc, argv, "c:tph", options, NULL)) != -1) {
        switch (option) {
        case 'c': config_path = optarg; break;
        case 't': check = true; break;
        case 'p': probe = true; break;
        case 'h': print_usage(argv[0]); return EXIT_SUCCESS;
        default: print_usage(argv[0]); return EXIT_FAILURE;
        }
    }
    if (optind != argc || (check && probe)) {
        print_usage(argv[0]);
        return EXIT_FAILURE;
    }
    if (config_path && setenv("PHONEBLOCK_CONFIG_FILE", config_path, 1) != 0) {
        perror("setenv PHONEBLOCK_CONFIG_FILE");
        return EXIT_FAILURE;
    }

    stats_setup();
    config_load();

    if (check) return check_config() ? EXIT_SUCCESS : EXIT_FAILURE;
    if (probe) return probe_sip();
    if (!check_config()) return EXIT_FAILURE;

    if (announcement_init() != PB_OK) {
        pb_log_err("linux", "announcement storage initialization failed");
        return EXIT_FAILURE;
    }

    // Block termination signals before creating workers. They inherit the
    // mask, leaving this thread as the single orderly shutdown point.
    sigset_t signals;
    sigemptyset(&signals);
    sigaddset(&signals, SIGINT);
    sigaddset(&signals, SIGTERM);
    struct sigaction pipe_action = { .sa_handler = SIG_IGN };
    sigemptyset(&pipe_action.sa_mask);
    if (sigaction(SIGPIPE, &pipe_action, NULL) != 0) {
        pb_log_err("linux", "could not ignore SIGPIPE: %s", strerror(errno));
        return EXIT_FAILURE;
    }
    int signal_error = pthread_sigmask(SIG_BLOCK, &signals, NULL);
    if (signal_error != 0) {
        pb_log_err("linux", "could not block termination signals: %s",
                   strerror(signal_error));
        return EXIT_FAILURE;
    }

    phoneblock_api_init();
    report_queue_start();
    if (config_phoneblock_token()[0]) {
        pb_log_info("linux", "initial PhoneBlock self-test");
        phoneblock_selftest(NULL);
    }
    sip_register_start();
    scheduler_start(false);

    pb_log_info("linux", "service started; waiting for SIGINT or SIGTERM");
    int received_signal = 0;
    int wait_error = sigwait(&signals, &received_signal);
    if (wait_error != 0) {
        pb_log_err("linux", "waiting for termination signal failed: %s",
                   strerror(wait_error));
        return EXIT_FAILURE;
    }
    pb_log_info("linux", "received signal %d; exiting", received_signal);
    return EXIT_SUCCESS;
}