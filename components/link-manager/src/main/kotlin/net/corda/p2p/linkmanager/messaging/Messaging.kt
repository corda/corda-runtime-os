package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.FlowMessage
import net.corda.p2p.HoldingIdentity
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toSessionNetworkMapPeer
import net.corda.p2p.linkmanager.sessions.SessionManager.Companion.toByteArray
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer

class Messaging {

    companion object {

        private val logger = LoggerFactory.getLogger(this::class.java.name)

        internal fun createLinkManagerToGatewayMessage(
            payload: Any,
            dest: HoldingIdentity,
            networkMap: LinkManagerNetworkMap
        ): LinkOutMessage {
            val header = generateLinkManagerToGatewayHeaderFromPeer(dest, networkMap)
                ?: throw IllegalArgumentException("Attempted to send message to peer: which is not in the network map.")
            return LinkOutMessage(header, payload)
        }

        private fun generateLinkManagerToGatewayHeaderFromPeer(
            peer: HoldingIdentity,
            networkMap: LinkManagerNetworkMap
        ): LinkOutHeader? {
            val endPoint = networkMap.getEndPoint(peer.toSessionNetworkMapPeer()) ?: return null
            return LinkOutHeader(endPoint.sni, endPoint.address)
        }

        fun createLinkManagerToGatewayMessageFromFlowMessage(
            message: FlowMessage,
            session: AuthenticatedSession,
            networkMap: LinkManagerNetworkMap
        ) : LinkOutMessage {
            val payload = message.toByteBuffer()
            val result = session.createMac(payload.toByteArray())
            val authenticatedMessage = AuthenticatedDataMessage(result.header, payload, ByteBuffer.wrap(result.mac))
            return createLinkManagerToGatewayMessage(
                authenticatedMessage,
                message.header.destination,
                networkMap
            )
        }

        fun authenticateAuthenticatedMessage(message: AuthenticatedDataMessage, session: AuthenticatedSession): FlowMessage? {
            try {
                session.validateMac(message.header, message.payload.array(), message.authTag.array())
            } catch (exception: InvalidMac) {
                logger.warn("MAC check failed for message for session ${message.header.sessionId}.")
                return null
            }
            return try {
                FlowMessage.fromByteBuffer(message.payload)
            } catch (exception: IOException) {
                logger.warn("Could not deserialize message for session ${message.header.sessionId}.")
                null
            }
        }
    }

}