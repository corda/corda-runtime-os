package net.corda.crypto.core.aes.ecdh.protocol

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.ecdh.AgreementParams

// stateful object, keeps the state as the protocol progresses
interface Initiator {
    val state: InitiatorState
    val encryptor: Encryptor
    fun createInitiatingHandshake(params: AgreementParams): InitiatingHandshake
    fun processReplyHandshake(reply: ReplyHandshake, info: ByteArray)
}