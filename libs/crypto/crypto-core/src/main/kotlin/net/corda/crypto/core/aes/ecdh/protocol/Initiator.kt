package net.corda.crypto.core.aes.ecdh.protocol

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.ecdh.ECDHAgreementParams
import net.corda.crypto.core.aes.ecdh.handshakes.InitiatingHandshake
import net.corda.crypto.core.aes.ecdh.handshakes.ReplyHandshake

// stateful object, keeps the state as the protocol progresses
interface Initiator {
    val state: InitiatorState
    val encryptor: Encryptor
    fun createInitiatingHandshake(params: ECDHAgreementParams): InitiatingHandshake
    fun processReplyHandshake(reply: ReplyHandshake, info: ByteArray)
}