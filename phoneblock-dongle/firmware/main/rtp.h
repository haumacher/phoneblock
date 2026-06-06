#pragma once

#include <stddef.h>
#include <stdint.h>
#include "lwip/sockets.h"

#include "announcement.h"

// The UDP port the dongle binds for RTP audio is configurable via
// config_rtp_port() (default 16000); the SDP advertises the same value.

// Spawn a FreeRTOS task that streams the opened announcement `src` to
// `dest` as G.711 A-law (PCMA) RTP, 20 ms / 160-byte frames at 50
// packets/s, then exits. The task reads `src` frame-by-frame (so a
// custom SPIFFS announcement is streamed straight from flash, never
// buffered whole) and closes it when done.
//
// Fire-and-forget: ownership of `src` (including its open file handle)
// is handed to the task — the caller must NOT close it afterwards.
// Even on failure to spawn, this releases `src`, so the caller is
// always relieved of it.
void rtp_play_audio(const struct sockaddr_in *dest,
                    announcement_src_t *src);

// Signal an in-flight rtp_play_audio task to stop at the next 20 ms
// frame boundary. Used by the SIP task to preempt the announcement
// when a second INVITE arrives while the first SPAM dialog is still
// streaming — no point making the new caller wait through 5–15 s of
// our own audio. Safe to call when no streaming task is active.
void rtp_request_abort(void);
