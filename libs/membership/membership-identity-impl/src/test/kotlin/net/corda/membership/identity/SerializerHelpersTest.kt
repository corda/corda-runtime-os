package net.corda.membership.identity

import net.corda.data.WireKeyValuePair
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SerializerHelpersTest {
    @Test
    fun `key order validation passes when map is ordered`() {
        val unorderedMap = listOf(
            WireKeyValuePair("apple", "pear"),
            WireKeyValuePair("key", "value")
        )
        assertDoesNotThrow { validateKeyOrder(unorderedMap) }
    }

    @Test
    fun `key order validation fails when map is not ordered`() {
        val unorderedMap = listOf(
            WireKeyValuePair("key", "value"),
            WireKeyValuePair("apple", "pear")
        )
        val ex = assertFailsWith<IllegalArgumentException> { validateKeyOrder(unorderedMap) }
        assertEquals(
            "The input was manipulated as it's expected to be ordered by first element in pairs.",
            ex.message
        )
    }
}