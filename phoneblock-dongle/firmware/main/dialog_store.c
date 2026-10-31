// Implementation of dialog_store.h.

#ifndef ESP_PLATFORM
#define _POSIX_C_SOURCE 200809L
#endif

#include "dialog_store.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

#ifdef ESP_PLATFORM
#define STORE_DIR "/spiffs"
#else
#define STORE_DIR PHONEBLOCK_DATA_DIR
#endif

#define FILE_PREFIX "dialog-"
#define INDEX_NAME  "dialog.idx"

void dialog_store_file_path(char *out, size_t cap, const char *lang)
{
    // lang is a validated locale code (config_lang_code_valid() or
    // dialog_set_parse()), so the name cannot escape the directory.
    snprintf(out, cap, "%s/" FILE_PREFIX "%s.alaw", STORE_DIR, lang);
}

static void index_path(char *out, size_t cap)
{
    snprintf(out, cap, "%s/" INDEX_NAME, STORE_DIR);
}

bool dialog_store_load(dialog_set_t *set, bool check_file)
{
    memset(set, 0, sizeof(*set));
    char path[DIALOG_STORE_PATH_CAP];
    index_path(path, sizeof(path));
    FILE *f = fopen(path, "r");
    if (!f) return false;
    // On the heap: this runs on the SIP task and an httpd worker.
    char *text = malloc(DIALOG_SET_TEXT_CAP);
    if (!text) {
        fclose(f);
        return false;
    }
    size_t n = fread(text, 1, DIALOG_SET_TEXT_CAP - 1, f);
    bool read_ok = !ferror(f);
    fclose(f);
    text[n] = '\0';
    bool parsed = read_ok && dialog_set_parse(text, set);
    free(text);
    if (!parsed) {
        memset(set, 0, sizeof(*set));
        return false;
    }
    if (!check_file) return true;

    dialog_store_file_path(path, sizeof(path), set->lang);
    struct stat st;
    if (stat(path, &st) != 0 || st.st_size <= 0 ||
        !dialog_set_within(set, (uint64_t)st.st_size)) {
        memset(set, 0, sizeof(*set));
        return false;
    }
    return true;
}

int dialog_store_write_index(const dialog_set_t *set)
{
    char *text = malloc(DIALOG_SET_TEXT_CAP);
    if (!text) return PB_FAIL;
    int n = dialog_set_format(set, text, DIALOG_SET_TEXT_CAP);
    char path[DIALOG_STORE_PATH_CAP];
    index_path(path, sizeof(path));
    FILE *f = n < 0 ? NULL : fopen(path, "w");
    if (!f) {
        free(text);
        return PB_FAIL;
    }
    bool ok = fwrite(text, 1, (size_t)n, f) == (size_t)n;
    free(text);
    if (fclose(f) != 0) ok = false;
    if (!ok) {
        unlink(path);
        return PB_FAIL;
    }
    return PB_OK;
}

void dialog_store_drop_index(void)
{
    char path[DIALOG_STORE_PATH_CAP];
    index_path(path, sizeof(path));
    unlink(path);
}

// Removes dialog-*.alaw and dialog-*.alaw.tmp, except the file of
// `keep_lang` (NULL: none).
static int remove_files(const char *keep_lang)
{
    char keep[40] = "";
    if (keep_lang) snprintf(keep, sizeof(keep), FILE_PREFIX "%s.alaw", keep_lang);
    int removed = 0;
    DIR *d = opendir(STORE_DIR);
    if (!d) return 0;
    struct dirent *e;
    while ((e = readdir(d)) != NULL) {
        if (strncmp(e->d_name, FILE_PREFIX, strlen(FILE_PREFIX)) != 0) continue;
        if (strstr(e->d_name, ".alaw") == NULL) continue;
        if (keep[0] && strcmp(e->d_name, keep) == 0) continue;
        // Sized for the directory + '/' + a full dirent name, so the path
        // can never be truncated (-Wformat-truncation).
        char file[DIALOG_STORE_PATH_CAP + sizeof(e->d_name)];
        snprintf(file, sizeof(file), "%s/%s", STORE_DIR, e->d_name);
        if (unlink(file) == 0) removed++;
    }
    closedir(d);
    return removed;
}

int dialog_store_remove_others(const char *keep_lang)
{
    return remove_files(keep_lang);
}

int dialog_store_remove_all(void)
{
    // The index first: without it nothing treats the file as a set any more,
    // whatever happens to the removal of the rest.
    char path[DIALOG_STORE_PATH_CAP];
    index_path(path, sizeof(path));
    int removed = unlink(path) == 0 ? 1 : 0;
    return removed + remove_files(NULL);
}
