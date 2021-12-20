package net.corda.crypto.component.config

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals

class CryptoRpcConfigTests {
    @Test
    @Timeout(5)
    fun `Should be able to use all extension properties`() {
        val raw = mapOf<String, Any?>(
            "rpc" to mapOf(
                "clientTimeoutMillis" to "11",
                "clientRetries" to "77"
            )
        )
        val config = CryptoLibraryConfigImpl(raw)
        assertEquals(11, config.rpc.clientTimeoutMillis)
        assertEquals(77, config.rpc.clientRetries)
    }

    @Test
    @Timeout(5)
    fun `CryptoRpcConfig should return default values if the value is not provided`() {
        val config = CryptoRpcConfig(emptyMap())
        assertEquals(15000, config.clientTimeoutMillis)
        assertEquals(1, config.clientRetries)
    }
}