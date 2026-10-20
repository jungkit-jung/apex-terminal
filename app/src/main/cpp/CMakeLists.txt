cmake_minimum_required(VERSION 3.22.1)
project("tradingterminal")

add_library(
    tradingterminal
    SHARED
    terminal_native.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    tradingterminal
    ${log-lib}
)
