#pragma once

#include "lwip/sockets.h"

// UDP port the dongle advertises for receiving/sending RTP audio.
// Fixed for v1; any inbound RTP is silently dropped.
#define SIP_RTP_PORT 16000

// Spawn a FreeRTOS task that plays a 440 Hz sine tone to the given
// destination for `duration_ms` milliseconds (PCMA, 20 ms frames), then
// exits. Fire-and-forget: ownership of the allocation the function makes
// internally is handed to the task.
void rtp_play_tone(const struct sockaddr_in *dest, int duration_ms);
