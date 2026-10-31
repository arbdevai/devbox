#define _GNU_SOURCE
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <android/log.h>
#include "vterm.h"
#include "vterm_internal.h"

#define TAG "DevBoxVTerm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define MAX_SCROLLBACK 2000

typedef struct {
    uint32_t codepoint;
    uint32_t fg_argb;
    uint32_t bg_argb;
    uint32_t flags; // 1: bold, 2: underline, 4: italic, 8: reverse, 16: wide, 32: wide_cont
} DevBoxCell;

typedef struct {
    int cols;
    DevBoxCell *cells;
} ScrollbackLine;

typedef struct {
    VTerm *vt;
    VTermScreen *screen;
    int rows;
    int cols;
    int cursor_row;
    int cursor_col;
    int cursor_visible;
    int altscreen_active;
    char *title;

    // Zero-GC Circular Ring Buffer for Scrollback History
    ScrollbackLine scrollback[MAX_SCROLLBACK];
    int sb_head;
    int sb_size;
} DevBoxTerminal;

static int cb_damage(VTermRect rect, void *user) {
    (void)rect;
    (void)user;
    return 1;
}

static int cb_moverect(VTermRect dest, VTermRect src, void *user) {
    (void)dest;
    (void)src;
    (void)user;
    return 1;
}

static int cb_movecursor(VTermPos pos, VTermPos oldpos, int visible, void *user) {
    (void)oldpos;
    DevBoxTerminal *term = (DevBoxTerminal *)user;
    term->cursor_row = pos.row;
    term->cursor_col = pos.col;
    term->cursor_visible = visible;
    return 1;
}

static int cb_settermprop(VTermProp prop, VTermValue *val, void *user) {
    DevBoxTerminal *term = (DevBoxTerminal *)user;
    if (prop == VTERM_PROP_CURSORVISIBLE) {
        term->cursor_visible = val->boolean;
    } else if (prop == VTERM_PROP_ALTSCREEN) {
        term->altscreen_active = val->boolean;
    } else if (prop == VTERM_PROP_TITLE) {
        if (val->string.initial) {
            if (term->title) free(term->title);
            term->title = NULL;
        }
        if (val->string.str && val->string.len > 0) {
            size_t oldlen = term->title ? strlen(term->title) : 0;
            char *newtitle = (char *)realloc(term->title, oldlen + val->string.len + 1);
            if (newtitle) {
                memcpy(newtitle + oldlen, val->string.str, val->string.len);
                newtitle[oldlen + val->string.len] = '\0';
                term->title = newtitle;
            }
        }
    }
    return 1;
}

static int cb_bell(void *user) {
    (void)user;
    return 1;
}

static int cb_sb_pushline(int cols, const VTermScreenCell *cells, void *user) {
    DevBoxTerminal *term = (DevBoxTerminal *)user;
    if (term->altscreen_active) return 1;

    int idx;
    if (term->sb_size < MAX_SCROLLBACK) {
        idx = (term->sb_head + term->sb_size) % MAX_SCROLLBACK;
        term->sb_size++;
    } else {
        idx = term->sb_head;
        term->sb_head = (term->sb_head + 1) % MAX_SCROLLBACK;
        if (term->scrollback[idx].cells) {
            free(term->scrollback[idx].cells);
            term->scrollback[idx].cells = NULL;
        }
    }

    term->scrollback[idx].cols = cols;
    term->scrollback[idx].cells = (DevBoxCell *)malloc(cols * sizeof(DevBoxCell));
    if (!term->scrollback[idx].cells) return 0;

    for (int c = 0; c < cols; c++) {
        DevBoxCell *dst = &term->scrollback[idx].cells[c];
        dst->codepoint = cells[c].chars[0];

        VTermColor fg = cells[c].fg;
        VTermColor bg = cells[c].bg;
        vterm_screen_convert_color_to_rgb(term->screen, &fg);
        vterm_screen_convert_color_to_rgb(term->screen, &bg);

        dst->fg_argb = VTERM_COLOR_IS_DEFAULT_FG(&cells[c].fg) ? 0x00000000 :
                       (0xFF000000 | (fg.rgb.red << 16) | (fg.rgb.green << 8) | fg.rgb.blue);
        dst->bg_argb = VTERM_COLOR_IS_DEFAULT_BG(&cells[c].bg) ? 0x00000000 :
                       (0xFF000000 | (bg.rgb.red << 16) | (bg.rgb.green << 8) | bg.rgb.blue);

        uint32_t flags = 0;
        if (cells[c].attrs.bold) flags |= 1;
        if (cells[c].attrs.underline) flags |= 2;
        if (cells[c].attrs.italic) flags |= 4;
        if (cells[c].attrs.reverse) flags |= 8;
        if (cells[c].width == 2) flags |= 16;
        if (cells[c].width == 0) flags |= 32;
        dst->flags = flags;
    }
    return 1;
}

static int cb_sb_popline(int cols, VTermScreenCell *cells, void *user) {
    DevBoxTerminal *term = (DevBoxTerminal *)user;
    if (term->sb_size <= 0) return 0;

    int last_idx = (term->sb_head + term->sb_size - 1) % MAX_SCROLLBACK;
    ScrollbackLine *line = &term->scrollback[last_idx];

    int copy_cols = cols < line->cols ? cols : line->cols;
    for (int c = 0; c < copy_cols; c++) {
        cells[c].chars[0] = line->cells[c].codepoint;
        cells[c].chars[1] = 0;
        cells[c].width = (line->cells[c].flags & 16) ? 2 : ((line->cells[c].flags & 32) ? 0 : 1);
        cells[c].attrs.bold = (line->cells[c].flags & 1) ? 1 : 0;
        cells[c].attrs.underline = (line->cells[c].flags & 2) ? 1 : 0;
        cells[c].attrs.italic = (line->cells[c].flags & 4) ? 1 : 0;
        cells[c].attrs.reverse = (line->cells[c].flags & 8) ? 1 : 0;

        uint32_t fg = line->cells[c].fg_argb;
        if (fg == 0) {
            cells[c].fg.type = VTERM_COLOR_DEFAULT_FG;
        } else {
            vterm_color_rgb(&cells[c].fg, (fg >> 16) & 0xFF, (fg >> 8) & 0xFF, fg & 0xFF);
        }
        uint32_t bg = line->cells[c].bg_argb;
        if (bg == 0) {
            cells[c].bg.type = VTERM_COLOR_DEFAULT_BG;
        } else {
            vterm_color_rgb(&cells[c].bg, (bg >> 16) & 0xFF, (bg >> 8) & 0xFF, bg & 0xFF);
        }
    }
    for (int c = copy_cols; c < cols; c++) {
        cells[c].chars[0] = 0;
        cells[c].width = 1;
        cells[c].fg.type = VTERM_COLOR_DEFAULT_FG;
        cells[c].bg.type = VTERM_COLOR_DEFAULT_BG;
    }

    if (line->cells) {
        free(line->cells);
        line->cells = NULL;
    }
    term->sb_size--;
    return 1;
}

static int cb_sb_clear(void *user) {
    DevBoxTerminal *term = (DevBoxTerminal *)user;
    if (!term) return 1;

    for (int i = 0; i < MAX_SCROLLBACK; i++) {
        if (term->scrollback[i].cells) {
            free(term->scrollback[i].cells);
            term->scrollback[i].cells = NULL;
        }
        term->scrollback[i].cols = 0;
    }
    term->sb_head = 0;
    term->sb_size = 0;
    return 1;
}

static VTermScreenCallbacks screen_callbacks = {
    .damage = cb_damage,
    .moverect = cb_moverect,
    .movecursor = cb_movecursor,
    .settermprop = cb_settermprop,
    .bell = cb_bell,
    .sb_pushline = cb_sb_pushline,
    .sb_popline = cb_sb_popline,
    .sb_clear = cb_sb_clear,
};

JNIEXPORT jlong JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeCreate(JNIEnv *env, jclass clazz, jint rows, jint cols) {
    (void)env;
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)calloc(1, sizeof(DevBoxTerminal));
    if (!term) return 0;

    term->rows = rows;
    term->cols = cols;
    term->cursor_row = 0;
    term->cursor_col = 0;
    term->cursor_visible = 1;
    term->altscreen_active = 0;
    term->title = NULL;
    term->sb_head = 0;
    term->sb_size = 0;

    term->vt = vterm_new(rows, cols);
    vterm_set_utf8(term->vt, 1);

    term->screen = vterm_obtain_screen(term->vt);
    vterm_screen_enable_altscreen(term->screen, 1);
    vterm_screen_set_callbacks(term->screen, &screen_callbacks, term);
    vterm_screen_reset(term->screen, 1);

    return (jlong)(intptr_t)term;
}

JNIEXPORT void JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeFeed(JNIEnv *env, jclass clazz, jlong ptr, jbyteArray bytes, jint offset, jint length) {
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term || !term->vt) return;

    jbyte *buf = (*env)->GetByteArrayElements(env, bytes, NULL);
    if (!buf) return;

    vterm_input_write(term->vt, (const char *)(buf + offset), (size_t)length);

    (*env)->ReleaseByteArrayElements(env, bytes, buf, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeResize(JNIEnv *env, jclass clazz, jlong ptr, jint rows, jint cols) {
    (void)env;
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term || !term->vt) return;

    term->rows = rows;
    term->cols = cols;
    vterm_set_size(term->vt, rows, cols);
}

JNIEXPORT void JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeClearScrollback(JNIEnv *env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term) return;
    cb_sb_clear(term);
}

JNIEXPORT jint JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeGetScrollbackSize(JNIEnv *env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term) return 0;
    return term->altscreen_active ? 0 : term->sb_size;
}

JNIEXPORT jboolean JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeIsAltScreen(JNIEnv *env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term) return JNI_FALSE;
    return term->altscreen_active ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeGetCursor(JNIEnv *env, jclass clazz, jlong ptr, jintArray outPos) {
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term) return;

    jint *arr = (*env)->GetIntArrayElements(env, outPos, NULL);
    if (!arr) return;

    arr[0] = term->cursor_row;
    arr[1] = term->cursor_col;
    arr[2] = term->cursor_visible;

    (*env)->ReleaseIntArrayElements(env, outPos, arr, 0);
}

JNIEXPORT jint JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeGetRowData(JNIEnv *env, jclass clazz, jlong ptr, jint lineIdx, jintArray outData) {
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term || !term->screen) return 0;

    int historyCount = term->altscreen_active ? 0 : term->sb_size;
    int cols = term->cols;

    jint *data = (*env)->GetIntArrayElements(env, outData, NULL);
    if (!data) return 0;

    if (lineIdx < historyCount) {
        // Baris dari C Ring Buffer Scrollback
        int bufIdx = (term->sb_head + lineIdx) % MAX_SCROLLBACK;
        ScrollbackLine *sbLine = &term->scrollback[bufIdx];
        int lineCols = sbLine->cols < cols ? sbLine->cols : cols;

        for (int c = 0; c < lineCols; c++) {
            DevBoxCell *cell = &sbLine->cells[c];
            int base = c * 4;
            data[base + 0] = (jint)cell->codepoint;
            data[base + 1] = (jint)cell->fg_argb;
            data[base + 2] = (jint)cell->bg_argb;
            data[base + 3] = (jint)cell->flags;
        }
        for (int c = lineCols; c < cols; c++) {
            int base = c * 4;
            data[base + 0] = 0;
            data[base + 1] = 0;
            data[base + 2] = 0;
            data[base + 3] = 0;
        }
    } else {
        // Baris aktif dari vterm_screen
        int activeRow = lineIdx - historyCount;
        if (activeRow >= 0 && activeRow < term->rows) {
            for (int c = 0; c < cols; c++) {
                VTermPos pos = { .row = activeRow, .col = c };
                VTermScreenCell cell;
                vterm_screen_get_cell(term->screen, pos, &cell);

                uint32_t cp = cell.chars[0];

                VTermColor fg = cell.fg;
                VTermColor bg = cell.bg;
                vterm_screen_convert_color_to_rgb(term->screen, &fg);
                vterm_screen_convert_color_to_rgb(term->screen, &bg);

                uint32_t fg_argb = VTERM_COLOR_IS_DEFAULT_FG(&cell.fg) ? 0x00000000 :
                                   (0xFF000000 | (fg.rgb.red << 16) | (fg.rgb.green << 8) | fg.rgb.blue);
                uint32_t bg_argb = VTERM_COLOR_IS_DEFAULT_BG(&cell.bg) ? 0x00000000 :
                                   (0xFF000000 | (bg.rgb.red << 16) | (bg.rgb.green << 8) | bg.rgb.blue);

                uint32_t flags = 0;
                if (cell.attrs.bold) flags |= 1;
                if (cell.attrs.underline) flags |= 2;
                if (cell.attrs.italic) flags |= 4;
                if (cell.attrs.reverse) flags |= 8;
                if (cell.width == 2) flags |= 16;
                if (cell.width == 0) flags |= 32;

                int base = c * 4;
                data[base + 0] = (jint)cp;
                data[base + 1] = (jint)fg_argb;
                data[base + 2] = (jint)bg_argb;
                data[base + 3] = (jint)flags;
            }
        }
    }

    (*env)->ReleaseIntArrayElements(env, outData, data, 0);
    return cols;
}

JNIEXPORT void JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeGetModes(JNIEnv *env, jclass clazz, jlong ptr, jintArray outModes) {
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term || !term->vt) return;

    jint *arr = (*env)->GetIntArrayElements(env, outModes, NULL);
    if (!arr) return;

    VTermState *state = vterm_obtain_state(term->vt);
    if (state) {
        arr[0] = state->mode.bracketpaste ? 1 : 0;
        arr[1] = (state->mouse_flags != 0) ? 1 : 0;
        arr[2] = (state->mouse_protocol == MOUSE_SGR) ? 1 : 0;
        arr[3] = state->mode.cursor ? 1 : 0;
    } else {
        arr[0] = 0;
        arr[1] = 0;
        arr[2] = 0;
        arr[3] = 0;
    }

    (*env)->ReleaseIntArrayElements(env, outModes, arr, 0);
}

JNIEXPORT jstring JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeGetTitle(JNIEnv *env, jclass clazz, jlong ptr) {
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term || !term->title) return NULL;
    return (*env)->NewStringUTF(env, term->title);
}

JNIEXPORT void JNICALL
Java_com_arbdev_devbox_terminal_NativeVTerm_nativeDestroy(JNIEnv *env, jclass clazz, jlong ptr) {
    (void)env;
    (void)clazz;
    DevBoxTerminal *term = (DevBoxTerminal *)(intptr_t)ptr;
    if (!term) return;

    for (int i = 0; i < MAX_SCROLLBACK; i++) {
        if (term->scrollback[i].cells) {
            free(term->scrollback[i].cells);
            term->scrollback[i].cells = NULL;
        }
    }

    if (term->title) {
        free(term->title);
        term->title = NULL;
    }

    if (term->vt) {
        vterm_free(term->vt);
        term->vt = NULL;
    }

    free(term);
}
