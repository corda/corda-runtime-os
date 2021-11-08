package net.corda.test.util

import kotlin.math.ceil

object LoggingUtils {
    // trying to make it easy to find the print lines in the very verbose osgi test logging
    fun String.emphasise(paddingChars: String = "#", width: Int = 80): String {
        val padding = paddingChars.repeat(
            kotlin.math.max(ceil((width - this.length - 2).toDouble() / 2).toInt(), 4)
        )
        return "$padding $this $padding"
    }
}