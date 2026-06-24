idf_component_register(
    SRCS "main.c" "sip_register.c" "sip_transport.c" "sip_frame.c" "sip_srv.c" "sip_auth.c" "api.c" "api_scan.c" "sip_parse.c" "audio.c" "rtp.c" "stats.c" "log_capture.c" "config.c" "smtp_body.c" "web.c" "web_auth.c" "tr064.c" "tr064_parse.c" "announcement.c" "scheduler.c" "sched_time.c" "time_sync.c" "mail.c" "sync.c" "selftest.c" "firmware_update.c" "version_cmp.c" "report_queue.c" "http_util.c" "wifi.c" "status_led.c" "crashreport.c" "logreport.c" "improv.c" "improv_proto.c" "blocklist_lookup.c" "blocklist_sync.c"
    INCLUDE_DIRS "."
    EMBED_FILES "audio/announcement.alaw" "web/index.html" "web/ab-logo-bot.svg"
    REQUIRES
        esp_http_client
        esp_http_server
        esp-tls
        mbedtls
        esp_netif
        esp_event
        esp_wifi
        wpa_supplicant
        esp_driver_gpio
        esp_driver_uart
        esp_timer
        esp_app_format
        nvs_flash
        spiffs
        json
        mdns
        app_update
        esp_https_ota
        espcoredump
        protocol_examples_common
)
