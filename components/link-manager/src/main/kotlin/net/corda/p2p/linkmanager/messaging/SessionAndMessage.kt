package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.Session
import org.slf4j.Logger
import org.slf4j.LoggerFactory

sealed class SessionAndMessage {
    companion object {

        private var logger = LoggerFactory.getLogger(this::class.java.name)

        private fun Logger.unknownMessageWarning(sessionType: String, sessionId: String, messageType: String) {
            this.error("The unknown message type $messageType cannot be processed by $sessionType with SessionId = $sessionId." +
                    " The message was discarded.")
        }

        fun create(session: Session, sessionId: String, message: Any): SessionAndMessage? {
            return when (session) {
                is AuthenticatedSession -> {
                    when (message) {
                        is AuthenticatedDataMessage -> Authenticated(session, message)
                        is AuthenticatedEncryptedDataMessage -> {
                            logger.warn(
                                "Received encrypted message for session with SessionId = $sessionId which is Authentication only." +
                                        " The message was discarded."
                            )
                            null
                        }
                        else -> {
                            logger.unknownMessageWarning(session::class.java.simpleName, sessionId, message::class.java.simpleName)
                            null
                        }
                    }
                }
                is AuthenticatedEncryptionSession -> {
                    when (message) {
                        is AuthenticatedEncryptedDataMessage -> AuthenticatedEncrypted(session, message)
                        is AuthenticatedDataMessage -> {
                            logger.warn(
                                "Received encrypted message for session with SessionId = $sessionId which is AuthenticationAndEncryption." +
                                        " The message was discarded."
                            )
                            null
                        }
                        else -> {
                            logger.unknownMessageWarning(session::class.java.simpleName, sessionId, message::class.java.simpleName)
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