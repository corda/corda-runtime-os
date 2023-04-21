package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.MessageType
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.CIPHER_ALGO
import net.corda.p2p.crypto.util.decrypt
import net.corda.p2p.crypto.util.encryptWithAssociatedData
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.Exception
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
import kotlin.experimental.xor

/**
 * A session established between two parties that allows authentication & encryption of data (prior to transmission),
 * as well as validation & decryption of them (post receipt).
 *
 * This class is thread-safe, which means multiple threads can try to encrypt & decrypt data concurrently using the same session.
 */
@Suppress("LongParameterList")
class AuthenticatedEncryptionSession(override val sessionId: String,
                                     nextSequenceNo: Long,
                                     private val outboundSecretKey: SecretKey,
                                     private val outboundNonce: ByteArray,
                                     private val inboundSecretKey: SecretKey,
                                     private val inboundNonce: ByteArray,
                                     val maxMessageSize: Int): Session {

    private val provider = BouncyCastleProvider()
    private val encryptionCipher = Cipher.getInstance(CIPHER_ALGO, provider)
    private val decryptionCipher = Cipher.getInstance(CIPHER_ALGO, provider)
    private val sequenceNo = AtomicLong(nextSequenceNo)

    fun encryptData(payload: ByteArray): EncryptionResult {
        if (payload.size > maxMessageSize) {
            throw MessageTooLargeError(payload.size, maxMessageSize)
        }

        val commonHeader = CommonHeader(MessageType.DATA, ProtocolConstants.PROTOCOL_VERSION, sessionId,
                                        sequenceNo.getAndIncrement(), Instant.now().toEpochMilli())

        val nonce = xor(outboundNonce, commonHeader.sequenceNo.toByteArray())
        val (encryptedData, authTag) =
            encryptionCipher.encryptWithAssociatedData(commonHeader.toByteBuffer().array(), nonce, payload, outboundSecretKey)
        return EncryptionResult(commonHeader, authTag, encryptedData)
    }

    /**
     * @throws DecryptionFailedError if decryption of the provided data failed, e.g because of invalid or modified data.
     */
    @Suppress("ThrowsCount")
    fun decryptData(header: CommonHeader, encryptedPayload: ByteArray, authTag: ByteArray): ByteArray {
        val nonce = xor(inboundNonce, header.sequenceNo.toByteArray())
        val plaintext = try {
            decryptionCipher.decrypt(header.toByteBuffer().array(), authTag, nonce, encryptedPayload, inboundSecretKey)
        } catch (e: Exception) {
            when(e) {
                is AEADBadTagException -> throw DecryptionFailedError("Decryption failed due to bad authentication tag.", e)
                is BadPaddingException -> throw DecryptionFailedError("Decryption failed due to bad padding.", e)
                is IllegalBlockSizeException -> throw DecryptionFailedError("Decryption failed due to bad block size", e)
                else -> throw e
            }
        }

        if (plaintext.size > maxMessageSize) {
            throw MessageTooLargeError(plaintext.size, maxMessageSize)
        }

        return plaintext
    }

    private fun xor(initialisationVector: ByteArray, seqNo: ByteArray): ByteArray {
        val paddingSize = initialisationVector.size - seqNo.size
        require(paddingSize >= 0)
        val paddedSeqNo = ByteArray(paddingSize + seqNo.size)
        System.arraycopy(seqNo, 0, paddedSeqNo, paddingSize, seqNo.size)

        return initialisationVector.zip(paddedSeqNo).map { (first, second) -> first xor second }
            .toList().toByteArray()
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

class DecryptionFailedError(msg: String, cause: Throwable): CordaRuntimeException(msg, cause)