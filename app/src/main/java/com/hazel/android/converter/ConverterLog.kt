package com.hazel.android.converter

/**
 * How to colour the one line the converter is currently showing.
 *
 * There is no log to go with it any more. While a conversion runs the screen shows what the
 * engine is doing now and nothing else, so all that is left of the old step list is the
 * question of whether the current line is ordinary progress or something gone wrong.
 */
enum class LogLevel { INFO, SUCCESS, WARN, ERROR }
