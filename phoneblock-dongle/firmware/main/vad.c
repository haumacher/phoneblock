// Implementation of vad.h.

#include "vad.h"

#include <limits.h>
#include <math.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

int vad_frame_dbfs(const int16_t *pcm, size_t n)
{
    if (n == 0) return VAD_DBFS_FLOOR;

    // Sum of squares in 64 bits: 160 samples of 32768^2 is ~1.7e11, past
    // 32 bits but far inside 64.
    uint64_t sum = 0;
    for (size_t i = 0; i < n; i++) {
        int32_t s = pcm[i];
        sum += (uint64_t)(s * s);
    }
    double mean_square = (double)sum / (double)n;
    if (mean_square < 1.0) return VAD_DBFS_FLOOR;   // log10(0) guard

    // 20*log10(rms / 32768) == 10*log10(mean_square / 32768^2): one log, no
    // square root.
    double db = 10.0 * log10(mean_square / (32768.0 * 32768.0));
    if (db < VAD_DBFS_FLOOR) db = VAD_DBFS_FLOOR;
    if (db > 0.0) db = 0.0;
    return (int)lround(db);
}

void vad_init(vad_t *v, int silence_db, int min_silence_ms)
{
    v->silence_db       = silence_db;
    v->min_silence_ms   = min_silence_ms;
    v->speaking         = false;  // so the FIRST utterance fires SPEECH_ONSET
    v->silence_ms       = 0;
    v->silence_reported = false;
    v->loud_run         = 0;
}

vad_event_t vad_process(vad_t *v, const int16_t *pcm, size_t n,
                        int frame_ms, int *out_dbfs)
{
    int db = vad_frame_dbfs(pcm, n);
    if (out_dbfs) *out_dbfs = db;

    if (db > v->silence_db) {          // strictly above the threshold: loud
        v->loud_run++;
        // A blip shorter than the confirm window is noise: it neither resets
        // nor advances the silence accumulator. Only sustained energy counts
        // as speech and restarts the timer.
        if (v->loud_run < VAD_SPEECH_CONFIRM_FRAMES) return VAD_NONE;
        v->silence_ms       = 0;
        v->silence_reported = false;
        if (!v->speaking) {
            v->speaking = true;
            return VAD_SPEECH_ONSET;
        }
        return VAD_NONE;
    }

    // Silent frame: accumulate, saturating so a very long silence cannot
    // wrap the counter.
    v->loud_run = 0;
    if (v->silence_ms < INT_MAX - frame_ms) v->silence_ms += frame_ms;

    if (!v->silence_reported && v->silence_ms >= v->min_silence_ms) {
        v->speaking         = false;
        v->silence_reported = true;
        return VAD_SILENCE_ONSET;
    }
    return VAD_NONE;
}
