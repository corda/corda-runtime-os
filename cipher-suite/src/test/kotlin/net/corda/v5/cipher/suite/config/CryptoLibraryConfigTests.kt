package net.corda.v5.cipher.suite.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoLibraryConfigTests {
    @Test
    @Timeout(5)
    fun `hasPath should return false if the key is not present`() {
        val map: Map<String, Any?> = mapOf(
            "key1" to "value1",
            "key2" to 42
        )
        assertFalse(map.hasPath("Whatever"))
    }

    @Test
    @Timeout(5)
    fun `hasPath should return false if the key is present but the value is null`() {
        val map: Map<String, Any?> = mapOf(
            "key1" to "value1",
            "key2" to 42,
            "Whatever" to null
        )
        assertFalse(map.hasPath("Whatever"))
    }

    @Test
    @Timeout(5)
    fun `hasPath should return true if the key is present and the value is not null`() {
        val map: Map<String, Any?> = mapOf(
            "key1" to "value1",
            "key2" to 42,
            "Whatever" to null
        )
        assertTrue(map.hasPath("key2"))
    }
}