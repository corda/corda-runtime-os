package net.corda.cipher.suite.impl.config

import com.typesafe.config.Config

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
}