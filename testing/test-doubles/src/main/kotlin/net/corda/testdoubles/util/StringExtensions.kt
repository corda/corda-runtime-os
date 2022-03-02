package net.corda.testdoubles.util

/**
 * Parsers a command and returns it as string. In the future it should handle double quotes and more advance syntax.
 */
fun String.parse(): Array<String> {
    val builders = mutableListOf(StringBuilder())

    var prevChar = ' '
    var withinQuotes = false

    for (c in this) {
        if (c == ' ')
            if (withinQuotes || prevChar == '\\')
                builders.last().append(c)
            else
                builders += StringBuilder()
        else if (c == '"')
            if (prevChar == '\\')
                builders.last().append(c)
            else
                withinQuotes = !withinQuotes
        else if (c != '\\')
            builders.last().append(c)

        prevChar = c
    }
    return builders.filter { it.isNotEmpty() }.map { it.toString() }.toTypedArray()
}
