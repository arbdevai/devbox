cmake_minimum_required(VERSION 3.22.1)

project("devbox-term")

set(CMAKE_C_STANDARD 99)

add_library(
    devbox-term
    SHARED
    libvterm/src/encoding.c
    libvterm/src/keyboard.c
    libvterm/src/mouse.c
    libvterm/src/parser.c
    libvterm/src/pen.c
    libvterm/src/screen.c
    libvterm/src/state.c
    libvterm/src/unicode.c
    libvterm/src/vterm.c
    devbox_vterm_jni.c
)

target_include_directories(
    devbox-term
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/libvterm/include
    ${CMAKE_CURRENT_SOURCE_DIR}/libvterm/src
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(
    log-lib
    log
)

target_link_libraries(
    devbox-term
    ${log-lib}
)
