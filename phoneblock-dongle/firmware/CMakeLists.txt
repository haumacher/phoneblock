cmake_minimum_required(VERSION 3.16)

# Reuse the WiFi/Ethernet connection helper from ESP-IDF's examples.
# Picking the single component directory avoids pulling in siblings that
# only build for the Linux target (protocol_examples_tapif_io).
set(EXTRA_COMPONENT_DIRS
    $ENV{IDF_PATH}/examples/common_components/protocol_examples_common
)

# Layer defaults: base file committed, optional .local overrides gitignored.
if(EXISTS "${CMAKE_CURRENT_LIST_DIR}/sdkconfig.defaults.local")
    set(SDKCONFIG_DEFAULTS
        "${CMAKE_CURRENT_LIST_DIR}/sdkconfig.defaults"
        "${CMAKE_CURRENT_LIST_DIR}/sdkconfig.defaults.local"
    )
endif()

# Derive the firmware version from the most recent `dongle-v*` git tag.
# Without this, ESP-IDF's fallback (project.cmake) runs a bare
# `git describe --tags`, which in this monorepo picks up unrelated tags
# (e.g. the web-app release tag `3.0.x`) and bakes them into
# esp_app_desc_t.version — the value reported by /api/status and crash
# reports.
#
# `release.sh` writes a clean version.txt before building; that file
# takes priority over a PROJECT_VER computed from `git describe`, so we
# only compute one here when version.txt is absent (i.e. dev builds).
# If git is unavailable or no tag matches, PROJECT_VER stays unset and
# ESP-IDF's own fallback applies — no regression.
if(NOT EXISTS "${CMAKE_CURRENT_LIST_DIR}/version.txt")
    find_package(Git QUIET)
    if(Git_FOUND)
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" -C "${CMAKE_CURRENT_LIST_DIR}"
                    describe --tags --match "dongle-v*" --always --dirty
            OUTPUT_VARIABLE dongle_ver
            OUTPUT_STRIP_TRAILING_WHITESPACE
            RESULT_VARIABLE dongle_ver_res
            ERROR_QUIET)
        if(dongle_ver_res EQUAL 0 AND dongle_ver)
            string(REGEX REPLACE "^dongle-v" "" PROJECT_VER "${dongle_ver}")
        endif()
    endif()
endif()

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(phoneblock_dongle)
