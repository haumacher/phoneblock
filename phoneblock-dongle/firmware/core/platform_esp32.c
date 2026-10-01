#include "platform.h"

#include <stdlib.h>

#include "esp_mac.h"
#include "esp_random.h"
#include "esp_task_wdt.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"

struct pb_mutex {
    SemaphoreHandle_t handle;
};

uint64_t pb_monotonic_us(void)
{
    return (uint64_t)esp_timer_get_time();
}

uint32_t pb_random_u32(void)
{
    return esp_random();
}

void pb_random_fill(void *out, size_t len)
{
    esp_fill_random(out, len);
}

int pb_watchdog_is_subscribed(void)
{
    return esp_task_wdt_status(NULL) == ESP_OK;
}

void pb_watchdog_subscribe(void)
{
    esp_task_wdt_add(NULL);
}

void pb_watchdog_reset(void)
{
    esp_task_wdt_reset();
}

void pb_task_sleep_ms(uint32_t milliseconds)
{
    vTaskDelay(pdMS_TO_TICKS(milliseconds));
}

void pb_task_yield(void)
{
    taskYIELD();
}

void pb_deadline_init(pb_deadline_t *deadline)
{
    deadline->at = xTaskGetTickCount();
}

void pb_task_delay_until(pb_deadline_t *deadline, uint32_t interval_ms)
{
    TickType_t at = (TickType_t)deadline->at;
    vTaskDelayUntil(&at, pdMS_TO_TICKS(interval_ms));
    deadline->at = at;
}

typedef struct {
    void (*fn)(void *);
    void *arg;
} pb_task_start_t;

static void pb_task_start(void *opaque)
{
    pb_task_start_t start = *(pb_task_start_t *)opaque;
    free(opaque);
    start.fn(start.arg);
    vTaskDelete(NULL);
}

bool pb_task_create(void (*fn)(void *), void *arg, const char *name,
                    size_t stack_bytes, pb_task_prio_t prio)
{
    if (!fn) return false;

    pb_task_start_t *start = malloc(sizeof(*start));
    if (!start) return false;
    start->fn = fn;
    start->arg = arg;

    // The values the tasks had before the platform layer: SIP 5, RTP 6 —
    // media above protocol work, so frames keep their pace while the SIP
    // task sits in a synchronous HTTPS lookup.
    UBaseType_t priority = prio == PB_PRIO_MEDIA ? 6 : 5;

    // ESP-IDF's FreeRTOS takes the stack depth in bytes (StackType_t is
    // uint8_t), matching the byte budget the caller passes.
    if (xTaskCreate(pb_task_start, name ? name : "phoneblock",
                    (configSTACK_DEPTH_TYPE)stack_bytes, start, priority,
                    NULL) != pdPASS) {
        free(start);
        return false;
    }
    return true;
}

pb_mutex_t *pb_mutex_create(void)
{
    pb_mutex_t *mutex = calloc(1, sizeof(*mutex));
    if (!mutex) return NULL;
    mutex->handle = xSemaphoreCreateMutex();
    if (!mutex->handle) {
        free(mutex);
        return NULL;
    }
    return mutex;
}

void pb_mutex_lock(pb_mutex_t *mutex)
{
    if (mutex) xSemaphoreTake(mutex->handle, portMAX_DELAY);
}

void pb_mutex_unlock(pb_mutex_t *mutex)
{
    if (mutex) xSemaphoreGive(mutex->handle);
}

int pb_get_mac(uint8_t mac[6])
{
    if (!mac) return -1;
    return esp_read_mac(mac, ESP_MAC_WIFI_STA) == ESP_OK ? 0 : -1;
}
