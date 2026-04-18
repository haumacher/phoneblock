#pragma once

#include <stddef.h>
#include <stdint.h>
#include "lwip/sockets.h"

// UDP port the dongle advertises for receiving/sending RTP audio.
// Fixed for v1; any inbound RTP is silently dropped.
#define SIP_RTP_PORT 16000

// Spawn a FreeRTOS task that streams a pre-encoded G.711 A-law (PCMA)
// payload to `dest` as RTP, 20 ms / 160-byte frames at 50 packets/s,
// then exits. Fire-and-forget: ownership of the internal allocation is
// handed to the task. `alaw_bytes` must point to a buffer that stays
// valid for the entire streaming duration — embedded flash data is
// ideal, which is the intended use.
void rtp_play_audio(const struct sockaddr_in *dest,
                    const uint8_t *alaw, size_t alaw_bytes);
