package net.corda.testdoubles.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StringExtensionsTest {
    @Test
    fun `i can parse a command`() {
        assertCommandParsing("one", arrayOf("one"))
        assertCommandParsing(" one", arrayOf("one"))
        assertCommandParsing("one ", arrayOf("one"))
        assertCommandParsing("one two", arrayOf("one", "two"))
        assertCommandParsing("one\\ two", arrayOf("one two"))
        assertCommandParsing("one\\two", arrayOf("onetwo"))
        assertCommandParsing("\"one two\"", arrayOf("one two"))
        assertCommandParsing("one \"two \\\"three\\\"\"", arrayOf("one", "two \"three\""))
        assertCommandParsing("--endpoint http://localhost:65325/", arrayOf("--endpoint", "http://localhost:65325/"))
    }

    private fun assertCommandParsing(actual: String, expected: Array<String>) {
        val parsed = parse(actual)
        assertEquals(expected.toList(), parsed.toList(), "expected command parse")
    }
}