package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.LinkManagerToGatewayHeader
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.Peer
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import java.util.concurrent.ConcurrentHashMap
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.linkmanager.sessions.SessionNetworkMap.Companion.toSessionNetworkMapPeer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer

class SessionManagerInitiator(
    private val mode: ProtocolMode,
    private val networkMap: SessionNetworkMap
    ) {

    companion object {
        internal fun ByteBuffer.toByteArray(): ByteArray {
            return ByteArray(this.capacity()) { this.get(it) }
        }

        internal fun generateLinkManagerMessage(payload: Any, dest: Peer, networkMap: SessionNetworkMap): LinkManagerToGatewayMessage {
            val header = generateLinkManagerToGatewayHeaderFromPeer(dest, networkMap)
                ?: throw IllegalArgumentException("Attempted to send message to peer: which is not in the network map.")
            return LinkManagerToGatewayMessage(header, payload)
        }

        private fun generateLinkManagerToGatewayHeaderFromPeer(peer: Peer, networkMap: SessionNetworkMap): LinkManagerToGatewayHeader? {
            val endPoint = networkMap.getEndPoint(peer.toSessionNetworkMapPeer()) ?: return null
            return LinkManagerToGatewayHeader(endPoint.sni, endPoint.address)
        }
    }

    private val pendingSessions = ConcurrentHashMap<String, Pair<Peer, AuthenticationProtocolInitiator>>()
    private val activeSessions = ConcurrentHashMap<Peer, AuthenticatedSession>()
    private val queuedMessages = ConcurrentLinkedQueue<LinkManagerToGatewayMessage>()
    private val queuedMessagesPendingSession = ConcurrentHashMap<Peer, ConcurrentLinkedQueue<FlowMessage>>()
    private val logger = LoggerFactory.getLogger(this::class.java.name)

    fun sendMessage(message: FlowMessage) {
        val session = activeSessions[message.header.destination]
        if (session != null) {
            val payload = message.toByteBuffer()
            val result = session.createMac(payload.toByteArray())
            val authenticatedMessage = AuthenticatedDataMessage(result.header, payload, ByteBuffer.wrap(result.mac))
            val processedMessage = generateLinkManagerMessage(authenticatedMessage, message.header.destination, networkMap)
            queuedMessages.add(processedMessage)
        } else {
            val newQueue = ConcurrentLinkedQueue<FlowMessage>()
            newQueue.add(message)
            val oldQueue = queuedMessagesPendingSession.putIfAbsent(message.header.destination, newQueue)
            if (oldQueue == null) {
                beginSessionNegotiation(message.header.destination)
            } else {
                oldQueue.add(message)
            }
        }
    }

    fun processSessionMessage(message: Any) {
        when(message) {
            is ResponderHelloMessage -> processResponderHello(message)
            is ResponderHandshakeMessage -> processResponderHandshake(message)
        }
    }

    fun getQueuedOutboundMessage(): LinkManagerToGatewayMessage {
        return queuedMessages.poll()
    }

    private fun beginSessionNegotiation(peer: Peer) {
        val sessionId = UUID.randomUUID().toString()
        val session = AuthenticationProtocolInitiator(sessionId, listOf(mode))
        pendingSessions[sessionId] = Pair(peer, session)
        val helloMessage = generateLinkManagerMessage(session.generateInitiatorHello(), peer, networkMap)
        queuedMessages.add(helloMessage)
    }

    private fun processResponderHello(message: ResponderHelloMessage) {
        val (sourceFromMemory, session) = pendingSessions[message.header.sessionId] ?: run {
            logger.warn("Received ${message::class.java::getSimpleName} with sessionId ${message.header.sessionId} " +
                    "but there is no pending session. The message was discarded.")
            return
        }

        session.receiveResponderHello(message)
        session.generateHandshakeSecrets()
        val ourKey = networkMap.getOurPublicKey()
        val responderKey = networkMap.getPublicKey(sourceFromMemory.toSessionNetworkMapPeer())
        if (responderKey == null) {
            logger.info("Received ${ResponderHelloMessage::class.java.simpleName} from peer " +
                "(${sourceFromMemory}) which is not in the network map.")
            return
        }
        val payload = session.generateOurHandshakeMessage(ourKey, responderKey, networkMap.getOurPeer().groupId ?: "", networkMap::signData)
        val outboundMessage = generateLinkManagerMessage(payload, sourceFromMemory, networkMap)
        queuedMessages.add(outboundMessage)
    }

    private fun processResponderHandshake(message: ResponderHandshakeMessage) {
        val (sourceFromMemory, session) = pendingSessions[message.header.sessionId] ?: run {
            logger.warn("Received ${message::class.java::getSimpleName} with sessionId = ${message.header.sessionId} " +
                "but there is no pending session. The message was discarded.")
            return
        }

        val responderKey = networkMap.getPublicKey(sourceFromMemory.toSessionNetworkMapPeer())
        if (responderKey == null) {
            logger.warn("Received ${ResponderHandshakeMessage::class.java.simpleName} from peer " +
                    "(${sourceFromMemory}) which is not in the network map.")
            return
        }
        try {
            session.validatePeerHandshakeMessage(message, responderKey)
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.warn("Received ${ResponderHandshakeMessage::class.java.simpleName} from peer " +
                "${sourceFromMemory} with sessionId = ${message.header.sessionId} which failed validation. " +
                 "The message was discarded.")
            return
        }

        activeSessions[sourceFromMemory] = session.getSession()
        pendingSessions.remove(message.header.sessionId)
        queuedMessagesPendingSession[sourceFromMemory]?.forEach { sendMessage(it) }
    }
}