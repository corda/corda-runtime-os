package net.corda.p2p.linkmanager.common

import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.Session
import org.slf4j.LoggerFactory

/**
 * An AVRO field which is a union type produces a Java field of type [Object]. These union types can be wrapped in a sealed class to make
 * the Link Manager more strongly typed.
 */
class AvroSealedClasses {

    companion object {
        private var logger = LoggerFactory.getLogger(this::class.java.name)
    }

    sealed class DataMessage {
        data class Authenticated(val authenticated: AuthenticatedDataMessage): DataMessage()
        data class AuthenticatedAndEncrypted(val encryptedDataMessage: AuthenticatedEncryptedDataMessage): DataMessage()
    }

    sealed class SessionAndMessage {
        companion object {

            fun create(session: Session, sessionId: String, message: DataMessage): SessionAndMessage? {
                return when (session) {
                    is AuthenticatedSession -> {
                        when (message) {
                            is DataMessage.Authenticated -> Authenticated(session, message.authenticated)
                            is DataMessage.AuthenticatedAndEncrypted -> {
                                logger.warn(
                                    "Received encrypted message for session with SessionId = $sessionId which is authentication only." +
                                            " The message was discarded."
                                )
                                null
                            }
                        }
                    }
                    is AuthenticatedEncryptionSession -> {
                        when (message) {
                            is DataMessage.AuthenticatedAndEncrypted -> AuthenticatedEncrypted(
                                session,
                                message.encryptedDataMessage
                            )
                            is DataMessage.Authenticated -> {
                                logger.warn(
                                    "Received unencrypted message for session with SessionId = $sessionId which is authentication and " +
                                            "encryption. The message was discarded."
                                )
                                null
                            }
                        }
                    }
                    else -> {
                        logger.warn("Invalid session type ${session::class.java}. The message was discarded.")
                        null
                    }
                }
            }
        }

        data class Authenticated(val session: AuthenticatedSession, val message: AuthenticatedDataMessage) :
            SessionAndMessage()

        data class AuthenticatedEncrypted(
            val session: AuthenticatedEncryptionSession,
            val message: AuthenticatedEncryptedDataMessage
        ) : SessionAndMessage()
    }
}