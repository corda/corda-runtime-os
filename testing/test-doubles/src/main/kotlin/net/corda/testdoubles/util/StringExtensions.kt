package net.corda.testdoubles.util

/**
 * Parsers a command and returns it as string. In the future it should handle double quotes and more advance syntax.
 */
fun String.parse(): Array<String> {
    return split(' ').map { it.trim() }.toTypedArray()
}
