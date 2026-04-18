idf_component_register(
    SRCS "main.c" "sip_register.c" "api.c" "sip_parse.c" "audio.c" "rtp.c" "stats.c" "config.c" "web.c"
    INCLUDE_DIRS "."
    EMBED_FILES "audio/announcement.alaw" "web/index.html"
    REQUIRES
        esp_http_client
        esp_http_server
        esp-tls
        esp_netif
        esp_event
        esp_timer
        esp_app_format
        nvs_flash
        json
        protocol_examples_common
)
