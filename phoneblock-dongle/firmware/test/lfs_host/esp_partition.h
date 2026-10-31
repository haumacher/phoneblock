// Host stand-in for IDF's esp_partition.h — the lookup and read storage.c's
// partition_content() does, on the simulation's RAM flash.
#pragma once
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

typedef enum { ESP_PARTITION_TYPE_DATA = 0x01 } esp_partition_type_t;
typedef enum { ESP_PARTITION_SUBTYPE_ANY = 0xff } esp_partition_subtype_t;

typedef struct {
    uint32_t size;
    char     label[17];
} esp_partition_t;

// The one "storage" partition; NULL for any other label.
const esp_partition_t *esp_partition_find_first(esp_partition_type_t type,
                                                esp_partition_subtype_t subtype,
                                                const char *label);
esp_err_t esp_partition_read(const esp_partition_t *p, size_t offset,
                             void *dst, size_t size);
