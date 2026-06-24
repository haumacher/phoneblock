#include "vad.h"

#include <math.h>
#include <limits.h>

int vad_frame_dbfs(const int16_t *pcm, size_t n)
{
    if (n == 0) return VAD_DBFS_FLOOR;

    // Sum of squares in double to avoid 32-bit overflow: 160 samples of
    // 32767^2 is ~1.7e11, well past INT32 but trivial for a double.
    double sum = 0.0;
    for (size_t i = 0; i < n; i++) {
        double s = (double)pcm[i];
        sum += s * s;
    }
    double rms = sqrt(sum / (double)n);
    if (rms < 1.0) return VAD_DBFS_FLOOR;   // log10(0) guard

    double db = 20.0 * log10(rms / 32768.0);
    if (db < VAD_DBFS_FLOOR) db = VAD_DBFS_FLOOR;
    if (db > 0.0) db = 0.0;
    return (int)lround(db);
}

void vad_init(vad_t *v, int silence_db, int min_silence_ms)
{
    v->silence_db       = silence_db;
    v->min_silence_ms   = min_silence_ms;
    v->speaking         = true;   // see header: detect leading silence too
    v->silence_ms       = 0;
    v->silence_reported = false;
    v->loud_run         = 0;
}

vad_event_t vad_process(vad_t *v, const int16_t *pcm, size_t n,
                        int frame_ms, int *out_dbfs)
{
    int db = vad_frame_dbfs(pcm, n);
    if (out_dbfs) *out_dbfs = db;

    bool loud = db > v->silence_db;   // strictly above threshold = speech
    if (loud) {
        v->loud_run++;
        // A blip shorter than the confirm window is treated as noise: it
        // neither resets nor advances the silence accumulator (held). Only
        // sustained energy counts as speech and restarts the timer.
        if (v->loud_run < VAD_SPEECH_CONFIRM_FRAMES) {
            return VAD_NONE;
        }
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
