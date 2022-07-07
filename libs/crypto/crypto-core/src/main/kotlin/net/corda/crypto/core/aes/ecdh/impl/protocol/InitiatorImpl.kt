package net.corda.crypto.core.aes.ecdh.impl.protocol

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.aes.ecdh.ECDHAgreementParams
import net.corda.crypto.core.aes.ecdh.ECDHFactory.Companion.HKDF_INITIAL_KEY_INFO
import net.corda.crypto.core.aes.ecdh.EphemeralKeyPair
import net.corda.crypto.core.aes.ecdh.asBytes
import net.corda.crypto.core.aes.ecdh.fromBytes
import net.corda.crypto.core.aes.ecdh.protocol.InitiatingHandshake
import net.corda.crypto.core.aes.ecdh.protocol.ReplyHandshake
import net.corda.crypto.core.aes.ecdh.impl.EphemeralKeyPairImpl
import net.corda.crypto.core.aes.ecdh.protocol.Initiator
import net.corda.crypto.core.aes.ecdh.protocol.InitiatorState
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import java.security.PublicKey
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InitiatorImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val otherStablePublicKey: PublicKey // only used for handshake exchange
) : Initiator {

    private val lock: ReentrantLock = ReentrantLock(true)

    @Volatile
    private var _state: InitiatorState = InitiatorState.NEW

    @Volatile
    private var _params: ECDHAgreementParams? = null

    @Volatile
    private var _encryptor: Encryptor? = null

    private val ephemeralScheme: KeyScheme = schemeMetadata.findKeyScheme(otherStablePublicKey)

    private val ephemeralKeyPair: EphemeralKeyPair = EphemeralKeyPairImpl.create(
        schemeMetadata,
        ephemeralScheme
    )

    override val state: InitiatorState get() = _state

    override val encryptor: Encryptor get() = _encryptor
        ?: throw IllegalStateException("The initiator must be in '${InitiatorState.READY}' state.")

    override fun createInitiatingHandshake(params: ECDHAgreementParams): InitiatingHandshake = lock.withLock {
        if(_state != InitiatorState.NEW) {
            throw IllegalStateException("The initiator must be in '${InitiatorState.NEW}' state.")
        }
        _state = InitiatorState.INIT
        _params = params
        InitiatingHandshake(
            params = handshakeEncryptor().encrypt(params.asBytes()),
            ephemeralPublicKey = schemeMetadata.encodeAsByteArray(ephemeralKeyPair.publicKey)
        )
    }

    override fun processReplyHandshake(replyBytes: ByteArray, info: ByteArray) = lock.withLock {
        if(_state != InitiatorState.INIT) {
            throw IllegalStateException("The initiator must be in '${InitiatorState.INIT}' state.")
        }
        val reply = handshakeEncryptor().decrypt(replyBytes).fromBytes<ReplyHandshake>()
        _encryptor = ephemeralKeyPair.deriveSharedEncryptor(
            otherEphemeralPublicKey = schemeMetadata.decodePublicKey(reply.ephemeralPublicKey),
            params = _params!!,
            info = info
        )
        _state = InitiatorState.READY
    }

    private fun handshakeEncryptor() = ephemeralKeyPair.deriveSharedEncryptor(
        otherEphemeralPublicKey = otherStablePublicKey,
        params = _params!!,
        info = HKDF_INITIAL_KEY_INFO
    )
}