cmake_minimum_required(VERSION 3.22.1)
project("xtsec")

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Os -fvisibility=hidden -fdata-sections -ffunction-sections")
set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,-s")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(OPENSSL_ROOT ${CMAKE_SOURCE_DIR}/openssl)

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES
        IMPORTED_LOCATION ${OPENSSL_ROOT}/lib/${ANDROID_ABI}/libcrypto.a
)

include_directories(${OPENSSL_ROOT}/include)

add_library(xtsec SHARED
        security.cpp
        xt_utils.cpp
        frida_detect.cpp
)

find_library(log-lib log)

target_link_libraries(xtsec
        crypto
        ${log-lib}
)
