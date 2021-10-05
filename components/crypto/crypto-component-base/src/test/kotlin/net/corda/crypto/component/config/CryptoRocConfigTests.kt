package net.corda.crypto.component.config

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.config.isDev
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CryptoRocConfigTests {
    @Test
    @Timeout(5)
    fun `Should be able to use all helper properties`() {
        val raw = mapOf<String, Any?>(
            "rpc" to mapOf(
                "groupName" to "rpcGroupName",
                "clientName" to "rpcClientName",
                "signingRequestTopic" to "rpcSigningRequestTopic",
                "freshKeysRequestTopic" to "rpcFreshKeysRequestTopic",
                "clientTimeout" to "11",
                "clientRetries" to "77"
            )
        )
        val config = CryptoLibraryConfigImpl(raw)
        assertFalse(config.isDev)
        assertEquals("rpcGroupName", config.rpc.groupName)
        assertEquals("rpcClientName", config.rpc.clientName)
        assertEquals("rpcSigningRequestTopic", config.rpc.signingRequestTopic)
        assertEquals("rpcFreshKeysRequestTopic", config.rpc.freshKeysRequestTopic)
        assertEquals(11, config.rpc.clientTimeout)
        assertEquals(77, config.rpc.clientRetries)
    }

    @Test
    @Timeout(5)
    fun `CryptoRpcConfig should return default values if the value is not provided`() {
        val config = CryptoRpcConfig(emptyMap())
        assertEquals("crypto.rpc", config.groupName)
        assertEquals("crypto.rpc", config.clientName)
        assertEquals("crypto.rpc.signing", config.signingRequestTopic)
        assertEquals("crypto.rpc.freshKeys", config.freshKeysRequestTopic)
        assertEquals(15, config.clientTimeout)
        assertEquals(1, config.clientRetries)
    }

    @Test
    @Timeout(5)
    fun `CryptoRpcConfig should create RPC config for signing service`() {
        val raw = mapOf(
            "groupName" to "rpcGroupName",
            "clientName" to "rpcClientName",
            "signingRequestTopic" to "rpcSigningRequestTopic",
            "freshKeysRequestTopic" to "rpcFreshKeysRequestTopic"

        )
        val config = CryptoRpcConfig(raw)
        val rpc = config.signingRpcConfig
        assertEquals("rpcGroupName", rpc.groupName)
        assertEquals("rpcClientName", rpc.clientName)
        assertEquals("rpcSigningRequestTopic", rpc.requestTopic)
        assertEquals(WireSigningRequest::class.java, rpc.requestType)
        assertEquals(WireSigningResponse::class.java, rpc.responseType)
    }

    @Test
    @Timeout(5)
    fun `CryptoRpcConfig should create RPC config for fresh keys service`() {
        val raw = mapOf(
            "groupName" to "rpcGroupName",
            "clientName" to "rpcClientName",
            "signingRequestTopic" to "rpcSigningRequestTopic",
            "freshKeysRequestTopic" to "rpcFreshKeysRequestTopic"

        )
        val config = CryptoRpcConfig(raw)
        val rpc = config.freshKeysRpcConfig
        assertEquals("rpcGroupName", rpc.groupName)
        assertEquals("rpcClientName", rpc.clientName)
        assertEquals("rpcFreshKeysRequestTopic", rpc.requestTopic)
        assertEquals(WireFreshKeysRequest::class.java, rpc.requestType)
        assertEquals(WireFreshKeysResponse::class.java, rpc.responseType)
    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'rpc' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals("crypto.rpc", config.rpc.groupName)
        assertEquals("crypto.rpc", config.rpc.clientName)
        assertEquals("crypto.rpc.signing", config.rpc.signingRequestTopic)
        assertEquals("crypto.rpc.freshKeys", config.rpc.freshKeysRequestTopic)
        assertEquals(15, config.rpc.clientTimeout)
        assertEquals(1, config.rpc.clientRetries)
    }
}