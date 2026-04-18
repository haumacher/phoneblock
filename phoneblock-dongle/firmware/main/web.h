#pragma once

// Start the on-device HTTP server that exposes the status + config web
// UI under http://<dongle-ip>/. Idempotent; safe to call once during
// app_main() after networking is up. Runs in its own FreeRTOS task
// spawned by esp_http_server.
void web_start(void);
