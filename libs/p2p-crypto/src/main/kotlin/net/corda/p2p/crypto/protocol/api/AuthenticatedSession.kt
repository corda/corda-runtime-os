package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.HMAC_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.PROTOCOL_VERSION
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocol.Companion.secureRandom
import net.corda.p2p.crypto.protocol.api.Session.Companion.toAvro
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.concurrent.withLock
import net.corda.data.p2p.crypto.protocol.Session as AvroSession

/**
 * A session established between two parties that allows authentication of data (prior to transmission) & validation of them (post receipt).
 *
 * This class is thread-safe, which means multiple threads can try to create & validate MACs concurrently using the same session.
 */
class AuthenticatedSession(
    override val sessionId: String,
    private val outboundSecretKey: SecretKey,
    private val inboundSecretKey: SecretKey,
    val maxMessageSize: Int,
) : Session {

    private val provider = BouncyCastleProvider.PROVIDER_NAME
    private val generationHMac = Mac.getInstance(HMAC_ALGO, provider).apply {
        this.init(outboundSecretKey)
    }
    private val validationHMac = Mac.getInstance(HMAC_ALGO, provider).apply {
        this.init(inboundSecretKey)
    }
    private val generationLock = ReentrantLock()
    private val validationLock = ReentrantLock()

    /**
     * Creates a message authentication code for the given payload and a header that is generated internally.
     * @return the header to be sent to the other party and the authentication code.
     */
    fun createMac(payload: ByteArray): AuthenticationResult {
        if (payload.size > maxMessageSize) {
            throw MessageTooLargeError(payload.size, maxMessageSize)
        }

        val commonHeader = CommonHeader(
            MessageType.DATA,
            PROTOCOL_VERSION,
            sessionId,
            secureRandom.nextLong(),
            Instant.now().toEpochMilli(),
        )
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
        if (payload.size > maxMessageSize) {
            throw MessageTooLargeError(payload.size, maxMessageSize)
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

    override fun toAvro(): AvroSession {
        return AvroSession(
            this.sessionId,
            this.maxMessageSize,
            AuthenticatedSessionDetails(
                outboundSecretKey.toAvro(),
                inboundSecretKey.toAvro(),
            ),
        )
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

class InvalidMac : CordaRuntimeException("The provided MAC was invalid.")
