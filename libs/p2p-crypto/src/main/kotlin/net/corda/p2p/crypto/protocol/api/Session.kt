package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.protocol.AuthenticatedEncryptionSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.nio.ByteBuffer
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import net.corda.data.p2p.crypto.protocol.SecretKeySpec as AvroSecretKeySpec
import net.corda.data.p2p.crypto.protocol.Session as AvroSession

/**
 * A marker interface supposed to be implemented by the different types of sessions supported by the authentication protocol.
 */
interface Session : SerialisableSessionData {
    val sessionId: String

    override fun toAvro(): AvroSession

    companion object {

        internal fun AvroSecretKeySpec.toSecretKey() =
            SecretKeySpec(
                this.key.array(),
                this.algorithm,
            )
        internal fun SecretKey.toAvro() =
            AvroSecretKeySpec(
                this.algorithm,
                ByteBuffer.wrap(this.encoded),
            )
        fun AvroSession.toCorda(): Session {
            return when (val details = this.details) {
                is AuthenticatedSessionDetails -> {
                    AuthenticatedSession(
                        sessionId = this.sessionId,
                        outboundSecretKey = details.outboundSecretKey.toSecretKey(),
                        inboundSecretKey = details.inboundSecretKey.toSecretKey(),
                        maxMessageSize = this.maxMessageSize,
                    )
                }
                is AuthenticatedEncryptionSessionDetails -> {
                    AuthenticatedEncryptionSession(
                        sessionId = this.sessionId,
                        outboundSecretKey = details.outboundSecretKey.toSecretKey(),
                        outboundNonce = details.outboundNonce.array(),
                        inboundSecretKey = details.inboundSecretKey.toSecretKey(),
                        inboundNonce = details.inboundNonce.array(),
                        maxMessageSize = this.maxMessageSize,
                    )
                }
                else -> throw CordaRuntimeException("Unexpected  session details: ${details?.javaClass}")
            }
        }
    }
}
