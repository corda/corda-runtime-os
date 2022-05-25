package net.corda.crypto.client.impl

import net.corda.data.KeyValuePair
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireUtilsTests {
    @Test
    fun `Should transform non empty wire context list to map`() {
        val list = listOf(
            KeyValuePair("key1", "value1"),
            KeyValuePair("key1", "value11"),
            KeyValuePair("key2", "value2")
        )
        val map = list.toMap()
        assertEquals(2, map.size)
        assertTrue(map.any { it.key == "key1" && it.value == "value11" })
        assertTrue(map.any { it.key == "key2" && it.value == "value2" })
    }

    @Test
    fun `Should transform empty wire context list to map`() {
        val list = emptyList<KeyValuePair>()
        val map = list.toMap()
        assertTrue(map.isEmpty())
    }
}