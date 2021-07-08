package net.corda.p2p.linkmanager.messaging

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
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toNetworkType
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.MemberInfo
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.NetworkType
import net.corda.p2p.payload.FlowMessage
import net.corda.p2p.payload.FlowMessageAndKey
import net.corda.p2p.payload.HoldingIdentity
import net.corda.p2p.payload.LinkManagerPayload
import net.corda.p2p.payload.MessageAck
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer

/**
 * This class contains code which can be used to convert between [LinkOutMessage]/[LinkInMessage] and
 * [FlowMessage] and vice-versa. It is also used to wrap session negotiation messages into [LinkOutMessage].
 */
class MessageConverter {

    companion object {

        private val logger = LoggerFactory.getLogger(this::class.java.name)

        internal fun createLinkOutMessage(payload: Any, dest: MemberInfo, networkType: NetworkType): LinkOutMessage {
            val header = generateLinkOutHeaderFromPeer(dest, networkType)
            return LinkOutMessage(header, payload)
        }

        private fun generateLinkOutHeaderFromPeer(peer: MemberInfo, networkType: NetworkType): LinkOutHeader {
            return LinkOutHeader(peer.holdingIdentity.x500Name, networkType.toNetworkType(), peer.endPoint.address)
        }

        fun linkOutMessageFromAck(
            message: MessageAck,
            source: HoldingIdentity,
            destination: HoldingIdentity,
            session: Session,
            networkMap: LinkManagerNetworkMap
        ): LinkOutMessage? {
            return createLinkOutMessageFromPayload(LinkManagerPayload(message), source, destination, session, networkMap)
        }

        fun linkOutMessageFromFlowMessageAndKey(
            message: FlowMessageAndKey,
            session: Session,
            networkMap: LinkManagerNetworkMap
        ): LinkOutMessage? {
            return createLinkOutMessageFromPayload(
                LinkManagerPayload(message),
                message.flowMessage.header.source,
                message.flowMessage.header.destination,
                session,
                networkMap
            )
        }

        private fun createLinkOutMessageFromPayload(
            payload: LinkManagerPayload,
            source: HoldingIdentity,
            destination: HoldingIdentity,
            session: Session,
            networkMap: LinkManagerNetworkMap
        ): LinkOutMessage? {
            val serializedPayload = payload.toByteBuffer()
            val result = when (session) {
                is AuthenticatedSession -> {
                    val result = session.createMac(serializedPayload.array())
                    AuthenticatedDataMessage(result.header, serializedPayload, ByteBuffer.wrap(result.mac))
                }
                is AuthenticatedEncryptionSession -> {
                    val result = session.encryptData(serializedPayload.array())
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

            val destMemberInfo = networkMap.getMemberInfo(destination.toHoldingIdentity())
            if (destMemberInfo == null) {
                logger.warn("Attempted to send message to peer $destination which is not in the network map. The message was discarded.")
                return null
            }
            val networkType = networkMap.getNetworkType(source.toHoldingIdentity())
            if (networkType == null) {
                logger.warn("Could not find the network type in the NetworkMap for our identity = ${source}. The message was discarded.")
                return null
            }

            return createLinkOutMessage(result, destMemberInfo, networkType)
        }

        fun extractPayload(session: Session, sessionId: String, message: Any): LinkManagerPayload? {
            val sessionAndMessage = SessionAndMessage.create(session, sessionId, message) ?: return null
            return when (sessionAndMessage) {
                is SessionAndMessage.Authenticated -> extractPayloadFromAuthenticatedMessage(sessionAndMessage)
                is SessionAndMessage.AuthenticatedEncrypted -> extractPayloadFromAuthenticatedEncryptedMessage(sessionAndMessage)
            }
        }

        fun extractPayloadFromAuthenticatedEncryptedMessage(
            sessionAndMessage: SessionAndMessage.AuthenticatedEncrypted
        ): LinkManagerPayload? {
            val message = sessionAndMessage.message
            val session = sessionAndMessage.session
            val decryptedData = try {
                session.decryptData(message.header, message.encryptedPayload.array(), message.authTag.array())
            } catch (exception: DecryptionFailedError) {
                logger.warn("Decryption failed for message for session ${message.header.sessionId}. Reason: ${exception.message} " +
                        "The message was discarded.")
                return null
            }
            return try {
                LinkManagerPayload.fromByteBuffer(ByteBuffer.wrap(decryptedData))
            } catch (exception: IOException) {
                logger.warn("Could not deserialize message for session ${message.header.sessionId}. The message was discarded.")
                null
            }
        }

        fun extractPayloadFromAuthenticatedMessage(sessionAndMessage: SessionAndMessage.Authenticated): LinkManagerPayload? {
            val message = sessionAndMessage.message
            val session = sessionAndMessage.session
            try {
                session.validateMac(message.header, message.payload.array(), message.authTag.array())
            } catch (exception: InvalidMac) {
                logger.warn("MAC check failed for message for session ${message.header.sessionId}. The message was discarded.")
                return null
            }
            return try {
                LinkManagerPayload.fromByteBuffer(message.payload)
            } catch (exception: IOException) {
                logger.warn("Could not deserialize message for session ${message.header.sessionId}. The message was discarded.")
                null
            }
        }
    }

}