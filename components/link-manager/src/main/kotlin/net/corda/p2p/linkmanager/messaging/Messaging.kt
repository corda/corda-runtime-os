package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.FlowMessage
import net.corda.p2p.HoldingIdentity
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.DecryptionFailedError
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toSessionNetworkMapPeer
import net.corda.v5.base.annotations.VisibleForTesting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer

class Messaging {

    companion object {

        private var logger = LoggerFactory.getLogger(this::class.java.name)

        @VisibleForTesting
        fun setLogger(newLogger: Logger) {
            logger = newLogger
        }

        internal fun createLinkOutMessage(
            payload: Any,
            dest: HoldingIdentity,
            networkMap: LinkManagerNetworkMap
        ): LinkOutMessage? {
            val header = generateLinkOutHeaderFromPeer(dest, networkMap)
            if (header == null) {
                logger.warn("Attempted to send message to peer $dest which is not in the network map. The message was discarded.")
                return null
            }
            return LinkOutMessage(header, payload)
        }

        private fun generateLinkOutHeaderFromPeer(
            peer: HoldingIdentity,
            networkMap: LinkManagerNetworkMap
        ): LinkOutHeader? {
            val endPoint = networkMap.getEndPoint(peer.toSessionNetworkMapPeer()) ?: return null
            return LinkOutHeader(endPoint.sni, endPoint.address)
        }

        fun createLinkOutMessageFromFlowMessage(
            message: FlowMessage,
            session: Session,
            networkMap: LinkManagerNetworkMap
        ): LinkOutMessage? {
            val payload = message.toByteBuffer()
            val result = when (session) {
                is AuthenticatedSession -> {
                    val result = session.createMac(payload.array())
                    AuthenticatedDataMessage(result.header, payload, ByteBuffer.wrap(result.mac))
                }
                is AuthenticatedEncryptionSession -> {
                    val result = session.encryptData(payload.array())
                    AuthenticatedEncryptedDataMessage(
                        result.header,
                        ByteBuffer.wrap(result.encryptedPayload),
                        ByteBuffer.wrap(result.authTag)
                    )
                }
                else -> {
                    logger.warn("Invalid Session type ${session::class.java.simpleName}.Session must be either " +
                        "${AuthenticatedSession::class.java.simpleName} or ${AuthenticatedEncryptionSession::class.java.simpleName}." +
                        " The message was discarded.")
                    return null
                }
            }
            return createLinkOutMessage(
                result,
                message.header.destination,
                networkMap
            )
        }

        fun convertToFlowMessage(session: Session, sessionId: String, message: LinkInMessage): FlowMessage? {
            val innerMessage = message.payload
            when (session) {
                is AuthenticatedSession -> {
                    if (innerMessage is AuthenticatedDataMessage) {
                        return convertAuthenticatedMessageToFlowMessage(innerMessage, session)
                    } else {
                        logger.warn("Received encrypted message for session with SessionId = $sessionId for which is " +
                                "Authentication only. The message was discarded.")
                    }
                }
                is AuthenticatedEncryptionSession -> {
                    if (innerMessage is AuthenticatedEncryptedDataMessage) {
                        return convertAuthenticatedEncryptedMessageToFlowMessage(innerMessage, session)
                    } else {
                        logger.warn("Received encrypted message for session with SessionId = $sessionId for which is " +
                                "AuthenticationAndEncryption. The message was discarded.")
                    }
                }
                else -> {
                    logger.warn("Invalid session type ${session::class.java} SessionId = $sessionId. The message was discarded.")
                }
            }
            return null
        }

        fun convertAuthenticatedEncryptedMessageToFlowMessage(
            message: AuthenticatedEncryptedDataMessage,
            session: AuthenticatedEncryptionSession
        ): FlowMessage? {
            val decryptedData = try {
                session.decryptData(message.header, message.encryptedPayload.array(), message.authTag.array())
            } catch (exception: DecryptionFailedError) {
                logger.warn("Decryption failed for message for session ${message.header.sessionId}. Reason: ${exception.message}." +
                        "The message was discarded.")
                return null
            }
            return try {
                FlowMessage.fromByteBuffer(ByteBuffer.wrap(decryptedData))
            } catch (exception: IOException) {
                logger.warn("Could not deserialize message for session ${message.header.sessionId}. The message was discarded.")
                null
            }
        }

        fun convertAuthenticatedMessageToFlowMessage(message: AuthenticatedDataMessage, session: AuthenticatedSession): FlowMessage? {
            try {
                session.validateMac(message.header, message.payload.array(), message.authTag.array())
            } catch (exception: InvalidMac) {
                logger.warn("MAC check failed for message for session ${message.header.sessionId}. The message was discarded.")
                return null
            }
            return try {
                FlowMessage.fromByteBuffer(message.payload)
            } catch (exception: IOException) {
                logger.warn("Could not deserialize message for session ${message.header.sessionId}. The message was discarded.")
                null
            }
        }
    }

}