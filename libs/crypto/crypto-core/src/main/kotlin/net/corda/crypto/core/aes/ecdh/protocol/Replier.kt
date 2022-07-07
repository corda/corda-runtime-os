package net.corda.crypto.core.aes.ecdh.protocol

import net.corda.crypto.core.Encryptor

interface Replier {
    val state: ReplierState
    val encryptor: Encryptor
    fun produceReplyHandshake(handshake: InitiatingHandshake, info: ByteArray): ReplyHandshake
}