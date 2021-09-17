package net.corda.crypto.impl.config

import com.typesafe.config.Config
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.subscription.factory.config.RPCConfig

class CryptoRpcConfig(private val raw: Config) {
    val groupName: String
        get() = if (raw.hasPath(this::groupName.name)) {
            raw.getString(this::groupName.name)
        } else {
            "crypto.rpc"
        }

    val clientName: String
        get() = if (raw.hasPath(this::clientName.name)) {
            raw.getString(this::clientName.name)
        } else {
            "crypto.rpc"
        }

    val signingRequestTopic: String
        get() = if (raw.hasPath(this::signingRequestTopic.name)) {
            raw.getString(this::signingRequestTopic.name)
        } else {
            "crypto.rpc.signing"
        }

    val freshKeysRequestTopic: String
        get() = if (raw.hasPath(this::freshKeysRequestTopic.name)) {
            raw.getString(this::freshKeysRequestTopic.name)
        } else {
            "crypto.rpc.freshKeys"
        }

    val clientTimeout: Long
        get() = if (raw.hasPath(this::clientTimeout.name)) {
            raw.getLong(this::clientTimeout.name)
        } else {
            15
        }

    val clientRetries: Long
        get() = if (raw.hasPath(this::clientRetries.name)) {
            raw.getLong(this::clientRetries.name)
        } else {
            1
        }

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