package net.corda.crypto.impl.config

import net.corda.v5.crypto.exceptions.CryptoConfigurationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoConfigMapTests {
    @Test
    @Timeout(5)
    fun `getOptionalConfig should return existing config`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getOptionalConfig("config")
        assertNotNull(value)
        assertEquals(2, value.size)
        assertTrue(value.any { it.key == "k1.k1" && it.value == "v1.v1" })
        assertTrue(value.any { it.key == "k1.k2" && it.value == 55 })
    }

    @Test
    @Timeout(5)
    fun `getOptionalConfig should return null if the key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getOptionalConfig("config2")
        assertNull(value)
    }

    @Test
    @Timeout(5)
    fun `getOptionalConfig should return null if the key is present but value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                ),
                "config2" to null
            )
        )
        val value = map.getOptionalConfig("config2")
        assertNull(value)
    }

    @Test
    @Timeout(5)
    fun `getConfig should return existing config`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getConfig("config")
        assertNotNull(value)
        assertEquals(2, value.size)
        assertTrue(value.any { it.key == "k1.k1" && it.value == "v1.v1" })
        assertTrue(value.any { it.key == "k1.k2" && it.value == 55 })
    }

    @Test
    @Timeout(5)
    fun `getConfig should throw CryptoConfigurationException if key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getConfig("config2")
        }
    }

    @Test
    @Timeout(5)
    fun `getConfig should throw CryptoConfigurationException if key is present but value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                ),
                "config2" to null
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getConfig("config2")
        }
    }

    @Test
    @Timeout(5)
    fun `getLong should return exiting value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getLong("key2")
        assertEquals(42, value)
    }

    @Test
    @Timeout(5)
    fun `getLong should return converted from string value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "47",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getLong("key2")
        assertEquals(47, value)
    }

    @Test
    @Timeout(5)
    fun `getLong should return converted from double value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 54.4,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getLong("key2")
        assertEquals(54, value)
    }

    @Test
    @Timeout(5)
    fun `getLong should throw CryptoConfigurationException if key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getLong("key!!!")
        }
    }

    @Test
    @Timeout(5)
    fun `getLong should throw CryptoConfigurationException if key is present but the value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to null,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getLong("key2")
        }
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `getLong should throw CryptoConfigurationException if key is present but the value is not number nor string`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to listOf(33),
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getLong("key2")
        }
    }

    @Test
    @Timeout(5)
    fun `getLong with default overload should return exiting value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getLong("key2", 57)
        assertEquals(42, value)
    }

    @Test
    @Timeout(5)
    fun `getLong with default overload should return converted from string value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "47",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getLong("key2", 32)
        assertEquals(47, value)
    }

    @Test
    @Timeout(5)
    fun `getLong with default overload should return converted from double value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 54.4,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getLong("key2", 91)
        assertEquals(54, value)
    }

    @Test
    @Timeout(5)
    fun `getLong with default overload should return default value if key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getLong("key!!!", 57)
        assertEquals(57, value)
    }

    @Test
    @Timeout(5)
    fun `getLong with default overload should return default value if key is present but the value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to null,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getLong("key2", 11)
        assertEquals(11, value)
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `getLong with default overload should throw CryptoConfigurationException if key is present but the value is not number nor string`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to listOf(33),
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getLong("key2", 32)
        }
    }

    @Test
    @Timeout(5)
    fun `getString should return exiting value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getString("key1")
        assertEquals("value1", value)
    }

    @Test
    @Timeout(5)
    fun `getString should return converted from int value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 47,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getString("key2")
        assertEquals("47", value)
    }

    @Test
    @Timeout(5)
    fun `getString should return toString value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to TestObject(),
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getString("key2")
        assertEquals("Hello World!", value)
    }

    @Test
    @Timeout(5)
    fun `getString should throw CryptoConfigurationException if key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getString("key!!!")
        }
    }

    @Test
    @Timeout(5)
    fun `getString should throw CryptoConfigurationException if key is present but the value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to null,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getString("key2")
        }
    }

    @Test
    @Timeout(5)
    fun `getString with default overload should return exiting value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getString("key1", "what?")
        assertEquals("value1", value)
    }

    @Test
    @Timeout(5)
    fun `getString with default overload should return converted from int value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 47,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getString("key2", "what!")
        assertEquals("47", value)
    }

    @Test
    @Timeout(5)
    fun `getString with default overload should return toString value`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to TestObject(),
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getString("key2", "what?")
        assertEquals("Hello World!", value)
    }

    @Test
    @Timeout(5)
    fun `getString with default overload should return default value if key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getString("key!!!", "what?")
        assertEquals("what?", value)
    }

    @Test
    @Timeout(5)
    fun `getString with default overload should return default value if key is present but the value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to null,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getString("key2", "what?")
        assertEquals("what?", value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return exiting value as true`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to true,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2")
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return exiting value as false`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to false,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2")
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return converted from string value as 1`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "1",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2")
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return converted from string value as true`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "true",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2")
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return converted from string value as 0`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "0",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2")
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return converted from string value as false`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "false",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2")
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return converted from double value as true`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 54.4,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getBoolean("key2")
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should return converted from double value as false`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 0,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getBoolean("key2")
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean should throw CryptoConfigurationException if key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getBoolean("key!!!")
        }
    }

    @Test
    @Timeout(5)
    fun `getBoolean should throw CryptoConfigurationException if key is present but the value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to null,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getBoolean("key2")
        }
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `getBoolean should throw CryptoConfigurationException if key is present but the value is not number nor string nor boolean`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to listOf(33),
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getBoolean("key2")
        }
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return exiting value as true`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to true,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2", false)
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return exiting value as false`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to false,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2", true)
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return converted from string value as 1`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "1",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2", false)
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return converted from string value as true`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "true",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2", false)
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return converted from string value as 0`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "0",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2", true)
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return converted from string value as false`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to "false",
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2", true)
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return converted from double value as true`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 54.4,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getBoolean("key2", false)
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return converted from double value as false`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 0,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 77
                )
            )
        )
        val value = map.getBoolean("key2", true)
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return default value if key is not present`() {
        val map = CryptoConfigMap(
            mapOf<String, Any?>(
                "key1" to "value1",
                "key2" to 42,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key!!!", true)
        assertTrue(value)
    }

    @Test
    @Timeout(5)
    fun `getBoolean with default overload should return default value if key is present but the value is null`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to null,
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        val value = map.getBoolean("key2", false)
        assertFalse(value)
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `getBoolean with default overload should throw CryptoConfigurationException if key is present but the value is not number nor string nor Boolean`() {
        val map = CryptoConfigMap(
            mapOf(
                "key1" to "value1",
                "key2" to listOf(33),
                "config" to mapOf<String, Any?>(
                    "k1.k1" to "v1.v1",
                    "k1.k2" to 55
                )
            )
        )
        assertThrows<CryptoConfigurationException> {
            map.getBoolean("key2", true)
        }
    }

    class TestObject {
        override fun toString(): String = "Hello World!"
    }
}
