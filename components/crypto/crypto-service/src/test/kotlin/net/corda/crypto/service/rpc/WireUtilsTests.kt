package net.corda.crypto.service.rpc

import net.corda.data.WireKeyValuePair
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireUtilsTests {
    @Test
    @Timeout(5)
    fun `Should transform non empty wire context list to map`() {
        val list = listOf(
            WireKeyValuePair("key1", "value1"),
            WireKeyValuePair("key1", "value11"),
            WireKeyValuePair("key2", "value2")
        )
        val map = list.toMap()
        assertEquals(2, map.size)
        assertTrue(map.any { it.key == "key1" && it.value == "value11" })
        assertTrue(map.any { it.key == "key2" && it.value == "value2" })
    }

    @Test
    @Timeout(5)
    fun `Should transform empty wire context list to map`() {
        val list = emptyList<WireKeyValuePair>()
        val map = list.toMap()
        assertTrue(map.isEmpty())
    }
}