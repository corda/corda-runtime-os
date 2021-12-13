package net.corda.membership.identity

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SerializerHelpersTest {
    @Test
    fun `key order validation passes when map is ordered`() {
        val orderedMap = listOf(
            KeyValuePair("apple", "pear"),
            KeyValuePair("key", "value")
        )
        assertDoesNotThrow { validateKeyOrder(KeyValuePairList(orderedMap)) }
    }

    @Test
    fun `key order validation fails when map is not ordered`() {
        val unorderedMap = listOf(
            KeyValuePair("key", "value"),
            KeyValuePair("apple", "pear")
        )
        val ex = assertFailsWith<IllegalArgumentException> { validateKeyOrder(KeyValuePairList(unorderedMap)) }
        assertEquals(
            "The input was manipulated as it's expected to be ordered by first element in pairs.",
            ex.message
        )
    }
}