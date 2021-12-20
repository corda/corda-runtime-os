package net.corda.crypto.component.config

import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.crypto.impl.config.DefaultConfigConsts
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig

class CryptoRpcConfig(
    map: Map<String, Any?>
) : CryptoConfigMap(map) {
    val clientTimeoutMillis: Long
        get() = getLong(this::clientTimeoutMillis.name, 15000)

    val clientRetries: Long
        get() = getLong(this::clientRetries.name, 1)
}

val CryptoLibraryConfig.rpc: CryptoRpcConfig get() =
    CryptoRpcConfig(CryptoConfigMap.getOptionalConfig(this, this::rpc.name) ?: emptyMap())
