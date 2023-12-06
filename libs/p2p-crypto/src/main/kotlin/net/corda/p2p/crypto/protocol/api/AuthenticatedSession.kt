package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.HMAC_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.PROTOCOL_VERSION
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolWrapper.Companion.secureRandom
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import kotlin.concurrent.withLock

/**
 * A session established between two parties that allows authentication of data (prior to transmission) & validation of them (post receipt).
 *
 * This class is thread-safe, which means multiple threads can try to create & validate MACs concurrently using the same session.
 */
class AuthenticatedSession(
    override val session: Session,
    private val details: AuthenticatedSessionDetails,
): SessionWrapper {
    override val sessionId: String
        get() = session.sessionId

    private val provider = BouncyCastleProvider.PROVIDER_NAME
    private val outboundSecretKey by lazy {
        details.outboundSecretKey.toSecretKey()
    }
    private val inboundSecretKey by lazy {
        details.inboundSecretKey.toSecretKey()
    }
    private val generationHMac by lazy {
        Mac.getInstance(HMAC_ALGO, provider).apply {
            this.init(outboundSecretKey)
        }
    }
    private val validationHMac by lazy {
        Mac.getInstance(HMAC_ALGO, provider).apply {
            this.init(inboundSecretKey)
        }
    }
    private val generationLock = ReentrantLock()
    private val validationLock = ReentrantLock()

    /**
     * Creates a message authentication code for the given payload and a header that is generated internally.
     * @return the header to be sent to the other party and the authentication code.
     */
    fun createMac(payload: ByteArray): AuthenticationResult {
        if (payload.size > session.maxMessageSize) {
            throw MessageTooLargeError(payload.size, session.maxMessageSize)
        }

        val commonHeader = CommonHeader(MessageType.DATA, PROTOCOL_VERSION, sessionId,
            secureRandom.nextLong(), Instant.now().toEpochMilli())
        val tag = generationLock.withLock {
            generationHMac.reset()
            generationHMac.update(commonHeader.toByteBuffer().array())
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
        if (payload.size > session.maxMessageSize) {
            throw MessageTooLargeError(payload.size, session.maxMessageSize)
        }

        val calculatedTag = validationLock.withLock {
            validationHMac.reset()
            validationHMac.update(header.toByteBuffer().array())
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

class InvalidMac: CordaRuntimeException("The provided MAC was invalid.")