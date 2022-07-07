package net.corda.crypto.core.aes.ecdh.impl.protocol

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.ecdh.EphemeralKeyPair
import net.corda.crypto.core.aes.ecdh.protocol.InitiatingHandshake
import net.corda.crypto.core.aes.ecdh.protocol.ReplyHandshake
import net.corda.crypto.core.aes.ecdh.impl.EphemeralKeyPairImpl
import net.corda.crypto.core.aes.ecdh.protocol.Replier
import net.corda.crypto.core.aes.ecdh.protocol.ReplierState
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import java.security.PublicKey
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ReplierImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val stablePublicKey: PublicKey // only used for the initial handshake
) : Replier {

    private val lock: ReentrantLock = ReentrantLock(true)

    @Volatile
    private var _state: ReplierState = ReplierState.NEW

    @Volatile
    private var _encryptor: Encryptor? = null

    private val ephemeralScheme: KeyScheme = schemeMetadata.findKeyScheme(stablePublicKey)

    private val ephemeralKeyPair: EphemeralKeyPair = EphemeralKeyPairImpl.create(schemeMetadata, ephemeralScheme)

    override val state: ReplierState get() = _state

    override val encryptor: Encryptor get() = _encryptor
        ?: throw IllegalStateException("The initiator must be in '${ReplierState.READY}' state.")

    override fun produceReplyHandshake(handshake: InitiatingHandshake, info: ByteArray): ReplyHandshake = lock.withLock {
        if(_state != ReplierState.NEW) {
            throw IllegalStateException("The initiator must be in '${ReplierState.NEW}' state.")
        }
        _encryptor = ephemeralKeyPair.deriveSharedEncryptor(
            otherEphemeralPublicKey = schemeMetadata.decodePublicKey(handshake.ephemeralPublicKey),
            params = handshake.params,
            info = info
        )
        _state = ReplierState.READY
        ReplyHandshake(
            ephemeralPublicKey = schemeMetadata.encodeAsByteArray(ephemeralKeyPair.publicKey),
            signature = ByteArray(0) // TODO: sign
        )
    }
}