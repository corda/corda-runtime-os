package net.corda.p2p.crypto.protocol.api

import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.CIPHER_ALGO
import net.corda.p2p.crypto.util.decrypt
import net.corda.p2p.crypto.util.encryptWithAssociatedData
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * A session established between two parties that allows authentication & encryption of data (prior to transmission),
 * as well as validation & decryption of them (post receipt).
 *
 * This class is thread-safe, which means multiple threads can try to encrypt & decrypt data concurrently using the same session.
 */
@Suppress("LongParameterList")
class AuthenticatedEncryptionSession(private val sessionId: String,
                                     nextSequenceNo: Long,
                                     private val outboundSecretKey: SecretKey,
                                     private val outboundNonce: ByteArray,
                                     private val inboundSecretKey: SecretKey,
                                     private val inboundNonce: ByteArray): Session {

    private val provider = BouncyCastleProvider()
    private val encryptionCipher = Cipher.getInstance(CIPHER_ALGO, provider)
    private val decryptionCipher = Cipher.getInstance(CIPHER_ALGO, provider)
    private val sequenceNo = AtomicLong(nextSequenceNo)

    fun encryptData(payload: ByteArray): EncryptionResult {
        val commonHeader = CommonHeader(MessageType.DATA, ProtocolConstants.PROTOCOL_VERSION, sessionId,
                                        sequenceNo.getAndIncrement(), Instant.now().toEpochMilli())

        val (encryptedData, authTag) =
            encryptionCipher.encryptWithAssociatedData(commonHeader.toByteBuffer().array(), outboundNonce, payload, outboundSecretKey)
        return EncryptionResult(commonHeader, authTag, encryptedData)
    }

    fun decryptData(header: CommonHeader, encryptedPayload: ByteArray, authTag: ByteArray): ByteArray {
        return decryptionCipher.decrypt(header.toByteBuffer().array(), authTag, inboundNonce, encryptedPayload, inboundSecretKey)
    }

}

data class EncryptionResult(val header: CommonHeader, val authTag: ByteArray, val encryptedPayload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptionResult

        if (header != other.header) return false
        if (!authTag.contentEquals(other.authTag)) return false
        if (!encryptedPayload.contentEquals(other.encryptedPayload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + encryptedPayload.contentHashCode()
        return result
    }

}