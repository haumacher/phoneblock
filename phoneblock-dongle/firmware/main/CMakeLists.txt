idf_component_register(
    SRCS "main.c" "sip_register.c" "sip_transport.c" "sip_frame.c" "sip_srv.c" "sip_auth.c" "api.c" "api_scan.c" "sip_parse.c" "audio.c" "rtp.c" "stats.c" "config.c" "web.c" "web_auth.c" "tr064.c" "tr064_parse.c" "announcement.c" "sync.c" "selftest.c" "firmware_update.c" "report_queue.c" "http_util.c" "wifi.c" "status_led.c" "pairing.c" "pairing_parse.c"
    INCLUDE_DIRS "."
    EMBED_FILES "audio/announcement.alaw" "web/index.html" "web/ab-logo-bot.svg"
    REQUIRES
        esp_http_client
        esp_http_server
        esp-tls
        esp_netif
        esp_event
        esp_wifi
        wpa_supplicant
        esp_driver_gpio
        esp_timer
        esp_app_format
        nvs_flash
        spiffs
        json
        mdns
        app_update
        esp_https_ota
        protocol_examples_common
)
