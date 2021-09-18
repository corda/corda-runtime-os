package net.corda.crypto.impl.config

import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.subscription.factory.config.RPCConfig

class CryptoRpcConfig(
    map: Map<String, Any?>
) : CryptoConfigMap(map) {
    val groupName: String
        get() = getString(this::groupName.name, "crypto.rpc")

    val clientName: String
        get() = getString(this::clientName.name, "crypto.rpc")

    val signingRequestTopic: String
        get() = getString(this::signingRequestTopic.name, "crypto.rpc.signing")

    val freshKeysRequestTopic: String
        get() = getString(this::freshKeysRequestTopic.name, "crypto.rpc.freshKeys")

    val clientTimeout: Long
        get() = getLong(this::clientTimeout.name, 15)

    val clientRetries: Long
        get() = getLong(this::clientRetries.name, 1)

    val signingRpcConfig get() = RPCConfig(
        groupName = groupName,
        clientName = clientName,
        requestTopic = signingRequestTopic,
        requestType = WireSigningRequest::class.java,
        responseType = WireSigningResponse::class.java
    )

    val freshKeysRpcConfig get() = RPCConfig(
        groupName = groupName,
        clientName = clientName,
        requestTopic = freshKeysRequestTopic,
        requestType = WireFreshKeysRequest::class.java,
        responseType = WireFreshKeysResponse::class.java
    )
}