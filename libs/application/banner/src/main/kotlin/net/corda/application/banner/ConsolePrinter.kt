package net.corda.application.banner

import kotlin.math.floor
import kotlin.math.max

class ConsolePrinter(private val linePrinter: (x: String) -> Unit = { it -> kotlin.io.println(it) }) {
    companion object {
        private const val SPACER = "*"
        private const val DEFAULT_LINE_WIDTH = 133
        private const val DEFAULT_PAD_WIDTH = 4
    }

    fun printPaddedLine(text: String, width: Int = DEFAULT_LINE_WIDTH) {
        val paddingSize = floor((width - text.length - 2).toDouble() / 2).toInt()
        val padding = SPACER.repeat(max(paddingSize, 2))
        val spaces = " ".repeat(width - text.length - (paddingSize*2) - 1)
        linePrinter("$padding ${text.replace(System.lineSeparator(), System.lineSeparator() + padding)}$spaces$padding")
    }

    fun println(text: String = "") {
        linePrinter(text)
    }

    fun printEmptyLine(width: Int = DEFAULT_LINE_WIDTH) {
        linePrinter(SPACER.repeat(width))
    }

    fun printLeftPad(text: String, padWidth: Int = DEFAULT_PAD_WIDTH) {
        val pad = " ".repeat(padWidth)
        linePrinter(text.lines().map { pad + it }.joinToString(System.lineSeparator()))
    }
}