package net.corda.crypto.core.aes.ecdh.protocol

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.ecdh.handshakes.InitiatingHandshake
import net.corda.crypto.core.aes.ecdh.handshakes.ReplyHandshake

interface Replier {
    val state: ReplierState
    val encryptor: Encryptor
    fun produceReplyHandshake(handshake: InitiatingHandshake, info: ByteArray): ReplyHandshake
}