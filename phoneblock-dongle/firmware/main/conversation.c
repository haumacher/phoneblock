#include "conversation.h"

// EMBED_FILES in main/CMakeLists.txt emits a start/end symbol pair per
// file; '-' and '.' in the name become '_'.
#define EMBED(sym) \
    extern const uint8_t _binary_##sym##_start[]; \
    extern const uint8_t _binary_##sym##_end[];

EMBED(hello_alaw)
EMBED(who_is_there_alaw)
EMBED(am_aparat_alaw)
EMBED(cant_hear_you_alaw)
EMBED(ask_question_1_alaw)
EMBED(ask_question_2_alaw)
EMBED(ask_question_3_alaw)

#define CLIP(sym) \
    (clip_t){ _binary_##sym##_start, \
              (size_t)(_binary_##sym##_end - _binary_##sym##_start) }

clip_t conversation_hello(void)         { return CLIP(hello_alaw); }
clip_t conversation_who_is_there(void)  { return CLIP(who_is_there_alaw); }
clip_t conversation_am_aparat(void)     { return CLIP(am_aparat_alaw); }
clip_t conversation_cant_hear_you(void) { return CLIP(cant_hear_you_alaw); }

clip_t conversation_question(uint32_t *rng)
{
    const clip_t q[3] = {
        CLIP(ask_question_1_alaw),
        CLIP(ask_question_2_alaw),
        CLIP(ask_question_3_alaw),
    };
    *rng = *rng * 1664525u + 1013904223u;   // advance the LCG
    return q[(*rng >> 24) % 3];
}
