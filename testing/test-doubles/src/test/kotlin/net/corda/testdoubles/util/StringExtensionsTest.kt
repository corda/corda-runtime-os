package net.corda.testdoubles.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StringExtensionsTest {
    @Test
    fun `i can parse a command`() {
        assertEquals(arrayOf("one two"), "one two".parse())
    }
}