package net.corda.crypto.client.rpc

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireUtilsTests {
    @Test
    fun `Should transform non empty map`() {
        val map = mapOf(
            "key1" to "value1",
            "key2" to "value2"
        )
        val result = map.toWire()
        assertEquals(2, result.size)
        assertTrue(result.any { it.key == "key1" && it.value == "value1" })
        assertTrue(result.any { it.key == "key2" && it.value == "value2" })
    }

    @Test
    fun `Should transform empty map`() {
        val map = emptyMap<String, String>()
        val result = map.toWire()
        assertTrue(result.isEmpty())
    }
}