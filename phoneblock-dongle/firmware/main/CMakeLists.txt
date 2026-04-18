idf_component_register(
    SRCS "main.c" "sip_register.c" "api.c" "sip_parse.c" "audio.c" "rtp.c" "stats.c"
    INCLUDE_DIRS "."
    EMBED_FILES "audio/announcement.alaw"
    REQUIRES
        esp_http_client
        esp-tls
        esp_netif
        esp_event
        esp_timer
        nvs_flash
        json
        protocol_examples_common
)
