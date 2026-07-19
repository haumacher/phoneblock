idf_component_register(
    SRCS "main.c" "sip_register.c" "sip_transport.c" "sip_frame.c" "sip_srv.c" "sip_auth.c" "api.c" "api_scan.c" "sip_parse.c" "audio.c" "rtp.c" "stats.c" "log_capture.c" "config.c" "smtp_body.c" "web.c" "web_auth.c" "tr064.c" "tr064_parse.c" "announcement.c" "scheduler.c" "sched_time.c" "time_sync.c" "mail.c" "mail_html.c" "mail_i18n.c" "sync.c" "selftest.c" "firmware_update.c" "version_cmp.c" "report_queue.c" "http_util.c" "wifi.c" "status_led.c" "crashreport.c" "logreport.c" "improv.c" "improv_proto.c" "blocklist_lookup.c" "blocklist_sync.c" "manifest_sig.c" "i18n_sync.c"
    INCLUDE_DIRS "."
    EMBED_FILES "web/index.html" "web/ab-logo-bot.svg"
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

# Bake the German web-UI strings into the image as the offline fallback,
# stripped (drop the ARB @key metadata) from the single normative source
# i18n/l10n/ui/ui_de.arb — so German is NOT duplicated in index.html and there
# is no extract/inject step. web.c serves this at /api/i18n/ui when no
# downloaded pack is present; the browser reads it same-origin.
set(_ui_de_arb  "${CMAKE_CURRENT_SOURCE_DIR}/../i18n/l10n/ui/ui_de.arb")
set(_ui_de_json "${CMAKE_CURRENT_BINARY_DIR}/ui_de.json")
add_custom_command(
    OUTPUT  "${_ui_de_json}"
    COMMAND python3 -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));json.dump({k:v for k,v in d.items() if k=='@@locale' or not k.startswith('@')},open(sys.argv[2],'w',encoding='utf-8'),ensure_ascii=False,separators=(',',':'))" "${_ui_de_arb}" "${_ui_de_json}"
    DEPENDS "${_ui_de_arb}"
    COMMENT "Stripping ui_de.arb -> ui_de.json (embedded German UI fallback)"
    VERBATIM)
target_add_binary_data(${COMPONENT_LIB} "${_ui_de_json}" TEXT DEPENDS "${_ui_de_json}")
