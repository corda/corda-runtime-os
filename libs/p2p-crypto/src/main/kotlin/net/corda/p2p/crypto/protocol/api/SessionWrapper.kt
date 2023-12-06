package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.protocol.AuthenticatedEncryptionSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * A marker interface supposed to be implemented by the different types of sessions supported by the authentication protocol.
 */
sealed interface SessionWrapper {
    companion object {
        fun wrap(session: Session): SessionWrapper {
            return when (val details = session.details) {
                is AuthenticatedEncryptionSessionDetails -> {
                    AuthenticatedEncryptionSession(session, details)
                }
                is AuthenticatedSessionDetails -> {
                    AuthenticatedSession(session, details)
                }
                else -> throw CordaRuntimeException("Invalid session type: ${details.javaClass.simpleName}")
            }
        }
    }
    val sessionId: String
    val session: Session
}
