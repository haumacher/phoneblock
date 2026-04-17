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

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(phoneblock_dongle)
