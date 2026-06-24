#pragma once

#include <stdint.h>
#include <stddef.h>

// Prerecorded dialog clips for the interactive answer bot, embedded in the
// firmware as 8 kHz G.711 A-law (so they ride along with every OTA update,
// unlike a SPIFFS image). See conversation.c for the role → file mapping.
typedef struct {
    const uint8_t *data;
    size_t         len;
} clip_t;

clip_t conversation_hello(void);          // "Guten Tag!"
clip_t conversation_who_is_there(void);   // "Wer ist da bitte?"
clip_t conversation_am_aparat(void);      // "Zu Diensten!"
clip_t conversation_cant_hear_you(void);  // "Haben Sie etwas gesagt?"

// One of the rotating "silly question" variants, picked from *rng (which
// is advanced) so the bot doesn't repeat the same question back-to-back.
clip_t conversation_question(uint32_t *rng);
