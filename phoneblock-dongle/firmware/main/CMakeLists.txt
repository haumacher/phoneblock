idf_component_register(
    SRCS "main.c" "sip_register.c" "sip_response.c" "sip_transport.c" "sip_frame.c" "sip_srv.c" "sip_auth.c" "api.c" "api_scan.c" "sip_parse.c" "audio.c" "rtp.c" "stats.c" "log_capture.c" "config.c" "smtp_body.c" "web.c" "web_auth.c" "web_access.c" "net_local.c" "tr064.c" "tr064_parse.c" "announcement.c" "scheduler.c" "sched_time.c" "time_sync.c" "mail.c" "mail_html.c" "sync.c" "phone_norm.c" "selftest.c" "firmware_update.c" "version_cmp.c" "report_queue.c" "http_util.c" "wifi.c" "status_led.c" "crashreport.c" "logreport.c" "improv.c" "improv_proto.c" "blocklist_lookup.c" "blocklist_sync.c"
    INCLUDE_DIRS "."
    EMBED_FILES "audio/announcement.alaw" "web/ab-logo-bot.svg"
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

# The dashboard SPA (web/index.html) is a self-contained ~204 KB blob whose
# .rodata dominates the app image. Gzip it at build time and embed the .gz
# instead of the raw file (~204 KB -> ~35 KB in flash); handle_root() in web.c
# serves it with "Content-Encoding: gzip". `gzip -9 -n` (no name/timestamp)
# keeps the output reproducible across builds. Regenerated automatically
# whenever web/index.html changes (MAIN_DEPENDENCY on the source).
set(index_html_src "${CMAKE_CURRENT_SOURCE_DIR}/web/index.html")
set(index_html_gz "${CMAKE_CURRENT_BINARY_DIR}/index.html.gz")
add_custom_command(
    OUTPUT "${index_html_gz}"
    COMMAND gzip -9 -n -c "${index_html_src}" > "${index_html_gz}"
    MAIN_DEPENDENCY "${index_html_src}"
    COMMENT "Gzipping dashboard SPA (web/index.html)"
    VERBATIM)
target_add_binary_data(${COMPONENT_LIB} "${index_html_gz}" BINARY
    DEPENDS "${index_html_gz}")
