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
    val groupName: String
        get() = getString(this::groupName.name, DefaultConfigConsts.Kafka.Rpc.GROUP_NAME)

    val clientId: String
        get() = getString(this::clientId.name, DefaultConfigConsts.Kafka.Rpc.CLIENT_ID)

    val signingRequestTopic: String
        get() = getString(this::signingRequestTopic.name, DefaultConfigConsts.Kafka.Rpc.SIGNING_REQUEST_TOPIC_NAME)

    val freshKeysRequestTopic: String
        get() = getString(this::freshKeysRequestTopic.name, DefaultConfigConsts.Kafka.Rpc.FRESH_KEY_REQUEST_TOPIC_NAME)

    val clientTimeout: Long
        get() = getLong(this::clientTimeout.name, 15)

    val clientRetries: Long
        get() = getLong(this::clientRetries.name, 1)

    val signingRpcConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RPCConfig(
            groupName = groupName,
            clientName = clientId,
            requestTopic = signingRequestTopic,
            requestType = WireSigningRequest::class.java,
            responseType = WireSigningResponse::class.java
        )
    }

    val freshKeysRpcConfig by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RPCConfig(
            groupName = groupName,
            clientName = clientId,
            requestTopic = freshKeysRequestTopic,
            requestType = WireFreshKeysRequest::class.java,
            responseType = WireFreshKeysResponse::class.java
        )
    }
}

val CryptoLibraryConfig.rpc: CryptoRpcConfig get() =
    CryptoRpcConfig(CryptoConfigMap.getOptionalConfig(this, this::rpc.name) ?: emptyMap())
