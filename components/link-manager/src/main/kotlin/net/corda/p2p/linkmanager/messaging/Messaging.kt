package net.corda.p2p.linkmanager.messaging

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AvroHoldingIdentity
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.LinkManagerToGatewayHeader
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.linkmanager.sessions.SessionManager.Companion.toByteArray
import net.corda.p2p.linkmanager.sessions.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.sessions.LinkManagerNetworkMap.Companion.toSessionNetworkMapPeer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer

class Messaging {

    companion object {

        private val logger = LoggerFactory.getLogger(this::class.java.name)

        internal fun createLinkManagerToGatewayMessage(
            payload: Any,
            dest: AvroHoldingIdentity,
            networkMap: LinkManagerNetworkMap
        ): LinkManagerToGatewayMessage {
            val header = generateLinkManagerToGatewayHeaderFromPeer(dest, networkMap)
                ?: throw IllegalArgumentException("Attempted to send message to peer: which is not in the network map.")
            return LinkManagerToGatewayMessage(header, payload)
        }

        private fun generateLinkManagerToGatewayHeaderFromPeer(
            peer: AvroHoldingIdentity,
            networkMap: LinkManagerNetworkMap
        ): LinkManagerToGatewayHeader? {
            val endPoint = networkMap.getEndPoint(peer.toSessionNetworkMapPeer()) ?: return null
            return LinkManagerToGatewayHeader(endPoint.sni, endPoint.address)
        }

        fun createLinkManagerToGatewayMessageFromFlowMessage(
            message: FlowMessage,
            session: AuthenticatedSession,
            networkMap: LinkManagerNetworkMap
        ) : LinkManagerToGatewayMessage {
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