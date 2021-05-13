package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.CommonHeader
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.RuntimeException
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.concurrent.withLock

/**
 * A session established between two parties that allows authentication data (prior to transmission) & validation of them (post receipt).
 *
 * This class is thread-safe, which means multiple threads can try to create & validate MACs concurrently using the same session.
 */
class AuthenticatedSession(private val sessionId: String,
                           nextSequenceNo: Long,
                           private val outboundSecretKey: SecretKey,
                           private val inboundSecretKey: SecretKey) {

    private val generationHMac = Mac.getInstance("HMac-SHA256", BouncyCastleProvider()).apply {
        this.init(outboundSecretKey)
    }
    private val validationHMac = Mac.getInstance("HMac-SHA256", BouncyCastleProvider()).apply {
        this.init(inboundSecretKey)
    }
    private val generationLock = ReentrantLock()
    private val validationLock = ReentrantLock()
    private val sequenceNo = AtomicLong(nextSequenceNo)

    /**
     * Creates a message authentication code for the given payload and a header that is generated internally.
     * @return the header to be sent to the other party and the authentication code.
     */
    fun createMac(payload: ByteArray): AuthenticationResult {
        val commonHeader = CommonHeader(MessageType.DATA, AuthenticationProtocol.PROTOCOL_VERSION, sessionId, sequenceNo.getAndIncrement(), Instant.now().toEpochMilli())
        val tag = generationLock.withLock {
            generationHMac.reset()
            generationHMac.update(commonHeader.toBytes())
            generationHMac.update(payload)
            generationHMac.doFinal()
        }

        return AuthenticationResult(commonHeader, tag)
    }

    /**
     * Validates the provided tag against the provided header & payload.
     * @throws InvalidMac if the provided MAC is not valid.
     */
    fun validateMac(header: CommonHeader, payload: ByteArray, tag: ByteArray) {
        val calculatedTag = validationLock.withLock {
            validationHMac.reset()
            validationHMac.update(header.toBytes())
            validationHMac.update(payload)
            validationHMac.doFinal()
        }

        if (!calculatedTag.contentEquals(tag)) {
            throw InvalidMac()
        }
    }

}

data class AuthenticationResult(val header: CommonHeader, val mac: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthenticationResult

        if (header != other.header) return false
        if (!mac.contentEquals(other.mac)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + mac.contentHashCode()
        return result
    }
}

class InvalidMac: RuntimeException()