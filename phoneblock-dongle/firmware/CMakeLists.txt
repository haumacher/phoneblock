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
# Either way the chosen version and where it came from is printed, because a
# version.txt that outlived its release run wins silently over git describe and
# then mis-stamps every build in the workspace — including the version under
# which a crash report gets filed, which is what a dump is symbolized against.
# One STATUS line makes that visible instead of mysterious.
if(EXISTS "${CMAKE_CURRENT_LIST_DIR}/version.txt")
    file(STRINGS "${CMAKE_CURRENT_LIST_DIR}/version.txt" dongle_ver_file
         LIMIT_COUNT 1)
    message(STATUS "Dongle version: ${dongle_ver_file} (from version.txt)")
else()
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
    message(STATUS "Dongle version: ${PROJECT_VER} (from git describe)")
endif()

# Refuse an older ESP-IDF (#350). v5.5.6 carries two years of Wi-Fi library
# fixes over the v5.3 that built up to 2.0.2, whose dongles hit heap
# corruption by received Wi-Fi frames. A build machine still on v5.3 must
# fail here instead of silently shipping the old libraries. (A version
# constraint in main/idf_component.yml does not do this: the component
# manager accepts v5.3 regardless.)
set(PB_MIN_IDF_VERSION "5.5.6")
include($ENV{IDF_PATH}/tools/cmake/version.cmake)
if("$ENV{IDF_VERSION}" VERSION_LESS "${PB_MIN_IDF_VERSION}")
    message(FATAL_ERROR
        "ESP-IDF $ENV{IDF_VERSION} at $ENV{IDF_PATH} is too old: "
        "the dongle firmware needs v${PB_MIN_IDF_VERSION} or newer.")
endif()

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(phoneblock_dongle)
