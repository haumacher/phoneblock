idf_component_register(
    SRCS "main.c" "sip_register.c" "sip_response.c" "sip_transport.c" "sip_frame.c" "sip_srv.c" "sip_auth.c" "api.c" "api_scan.c" "sip_parse.c" "audio.c" "rtp.c" "stats.c" "log_capture.c" "config.c" "smtp_body.c" "web.c" "web_auth.c" "web_access.c" "net_local.c" "tr064.c" "tr064_parse.c" "announcement.c" "scheduler.c" "sched_time.c" "time_sync.c" "mail.c" "mail_html.c" "mail_i18n.c" "sync.c" "phone_norm.c" "selftest.c" "firmware_update.c" "version_cmp.c" "report_queue.c" "http_util.c" "wifi.c" "status_led.c" "crashreport.c" "logreport.c" "improv.c" "improv_proto.c" "blocklist_lookup.c" "blocklist_sync.c" "manifest_sig.c" "i18n_sync.c"
    INCLUDE_DIRS "."
    # index.html is embedded gzipped via a custom command below; the
    # announcement is no longer baked in (issue #460 — downloaded per locale).
    EMBED_FILES "web/ab-logo-bot.svg"
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

# Bake the ENGLISH web-UI strings into the image as the universal offline
# fallback (most widely understood), stripped (drop the ARB @key metadata, keep
# @@locale) from i18n/l10n/ui/ui_en.arb. web.c serves this at /api/i18n/ui when
# no downloaded pack for the active locale is present; the browser reads it
# same-origin. German is NOT embedded — it is a normal downloaded locale like
# any other (its source ui_de.arb is the translation source).
set(_ui_en_arb  "${CMAKE_CURRENT_SOURCE_DIR}/../i18n/l10n/ui/ui_en.arb")
set(_ui_en_json "${CMAKE_CURRENT_BINARY_DIR}/ui_en.json")
add_custom_command(
    OUTPUT  "${_ui_en_json}"
    COMMAND python3 -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));json.dump({k:v for k,v in d.items() if k=='@@locale' or not k.startswith('@')},open(sys.argv[2],'w',encoding='utf-8'),ensure_ascii=False,separators=(',',':'))" "${_ui_en_arb}" "${_ui_en_json}"
    DEPENDS "${_ui_en_arb}"
    COMMENT "Stripping ui_en.arb -> ui_en.json (embedded English UI fallback)"
    VERBATIM)
target_add_binary_data(${COMPONENT_LIB} "${_ui_en_json}" TEXT DEPENDS "${_ui_en_json}")

# Same for the status-mail strings: bake the ENGLISH mail pack into the image as
# the universal offline fallback, stripped from i18n/l10n/mail/mail_en.arb.
# mail_i18n.c parses this once and uses it whenever the downloaded pack for the
# active ui_lang is missing/invalid. German is NOT compiled in — it is a normal
# downloaded locale (mail_de.arb is the translation source).
set(_mail_en_arb  "${CMAKE_CURRENT_SOURCE_DIR}/../i18n/l10n/mail/mail_en.arb")
set(_mail_en_json "${CMAKE_CURRENT_BINARY_DIR}/mail_en.json")
add_custom_command(
    OUTPUT  "${_mail_en_json}"
    COMMAND python3 -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));json.dump({k:v for k,v in d.items() if k=='@@locale' or not k.startswith('@')},open(sys.argv[2],'w',encoding='utf-8'),ensure_ascii=False,separators=(',',':'))" "${_mail_en_arb}" "${_mail_en_json}"
    DEPENDS "${_mail_en_arb}"
    COMMENT "Stripping mail_en.arb -> mail_en.json (embedded English mail fallback)"
    VERBATIM)
target_add_binary_data(${COMPONENT_LIB} "${_mail_en_json}" TEXT DEPENDS "${_mail_en_json}")

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
