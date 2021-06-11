package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.AvroHoldingIdentity
import net.corda.p2p.crypto.GatewayToLinkManagerMessage
import net.corda.p2p.crypto.LinkManagerToGatewayHeader
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import java.util.concurrent.ConcurrentHashMap
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.linkmanager.sessions.SessionNetworkMap.Companion.toAvroHoldingIdentity
import net.corda.p2p.linkmanager.sessions.SessionNetworkMap.Companion.toSessionNetworkMapPeer
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer

class SessionManagerInitiator(
    private val mode: ProtocolMode,
    private val networkMap: SessionNetworkMap,
    private val maxMessageSize: Int
    ) {

    companion object {
        internal fun ByteBuffer.toByteArray(): ByteArray {
            return ByteArray(this.capacity()) { this.get(it) }
        }

        internal fun generateLinkManagerMessage(
            payload: Any,
            dest: AvroHoldingIdentity,
            networkMap: SessionNetworkMap
        ): LinkManagerToGatewayMessage {
            val header = generateLinkManagerToGatewayHeaderFromPeer(dest, networkMap)
                ?: throw IllegalArgumentException("Attempted to send message to peer: which is not in the network map.")
            return LinkManagerToGatewayMessage(header, payload)
        }

        private fun generateLinkManagerToGatewayHeaderFromPeer(
            peer: AvroHoldingIdentity,
            networkMap: SessionNetworkMap
        ): LinkManagerToGatewayHeader? {
            val endPoint = networkMap.getEndPoint(peer.toSessionNetworkMapPeer()) ?: return null
            return LinkManagerToGatewayHeader(endPoint.sni, endPoint.address)
        }
    }

    //On the Initiator side there is a single unique session per set (
    data class SessionKey(val ourGroupId: String?, val responderId: SessionNetworkMap.NetMapHoldingIdentity)

    private val pendingSessions = ConcurrentHashMap<String, Pair<SessionKey, AuthenticationProtocolInitiator>>()
    private val activeSessions = ConcurrentHashMap<SessionKey, AuthenticatedSession>()
    private val queuedMessages = ConcurrentLinkedQueue<LinkManagerToGatewayMessage>()
    private val queuedMessagesPendingSession = ConcurrentHashMap<SessionKey, ConcurrentLinkedQueue<FlowMessage>>()
    private val logger = LoggerFactory.getLogger(this::class.java.name)

    fun sendMessage(message: FlowMessage) {
        val ourHoldingIdentity = networkMap.getOurHoldingIdentity(message.header.source.groupId) ?:
            throw IllegalArgumentException("Message Invalid: Our holding Identity ${message.header.source.groupId} is" +
                " not in the networkMap.")
        if (ourHoldingIdentity.x500Name != message.header.source.x500Name) {
            throw IllegalArgumentException("Message has invalid source identity")
        }
        val key = SessionKey(ourHoldingIdentity.groupId, message.header.destination.toSessionNetworkMapPeer())
        val session = activeSessions[key]

        if (session != null) {
            val payload = message.toByteBuffer()
            val result = session.createMac(payload.toByteArray())
            val authenticatedMessage = AuthenticatedDataMessage(result.header, payload, ByteBuffer.wrap(result.mac))
            val processedMessage = generateLinkManagerMessage(authenticatedMessage, message.header.destination, networkMap)
            queuedMessages.add(processedMessage)
        } else {
            val newQueue = ConcurrentLinkedQueue<FlowMessage>()
            newQueue.add(message)
            val oldQueue = queuedMessagesPendingSession.putIfAbsent(key, newQueue)
            if (oldQueue == null) {
                beginSessionNegotiation(key)
            } else {
                oldQueue.add(message)
            }
        }
    }

    fun processSessionMessage(message: GatewayToLinkManagerMessage) {
        when(val payload = message.payload) {
            is ResponderHelloMessage -> processResponderHello(payload)
            is ResponderHandshakeMessage -> processResponderHandshake(payload)
        }
    }

    fun getQueuedOutboundMessage(): LinkManagerToGatewayMessage {
        return queuedMessages.poll()
    }

    fun getQueuedOutboundMessages(): List<LinkManagerToGatewayMessage> {
        val messages = mutableListOf<LinkManagerToGatewayMessage>()
        for (i in 0 until queuedMessages.size) {
            val message = queuedMessages.element() ?: break
            messages.add(message)
        }
        return messages
    }

    private fun beginSessionNegotiation(sessionKey: SessionKey) {
        val sessionId = UUID.randomUUID().toString()
        val session = AuthenticationProtocolInitiator(sessionId, listOf(mode), maxMessageSize)
        pendingSessions[sessionId] = Pair(sessionKey, session)
        val helloMessage = generateLinkManagerMessage(
            session.generateInitiatorHello(),
            sessionKey.responderId.toAvroHoldingIdentity(),
            networkMap
        )
        queuedMessages.add(helloMessage)
    }

    fun processResponderHello(message: ResponderHelloMessage) {
        val (sessionInfo, session) = pendingSessions[message.header.sessionId] ?: run {
            logger.warn("Received ${message::class.java::getSimpleName} with sessionId ${message.header.sessionId} " +
                    "but there is no pending session. The message was discarded.")
            return
        }

        session.receiveResponderHello(message)
        session.generateHandshakeSecrets()
        val ourKey = networkMap.getOurPublicKey(sessionInfo.ourGroupId)
        if (ourKey == null) {
            logger.info("Cannot find public key for our Holding Identity ${sessionInfo.ourGroupId}.")
            return
        }

        val responderKey = networkMap.getPublicKey(sessionInfo.responderId)
        if (responderKey == null) {
            logger.info("Received ${ResponderHelloMessage::class.java.simpleName} from peer " +
                "(${sessionInfo}) which is not in the network map. The message was discarded.")
            return
        }
        val signData = {it : ByteArray -> (networkMap::signData)(sessionInfo.ourGroupId, it)}
        val groupIdOrEmpty = sessionInfo.ourGroupId ?: ""
        val payload = session.generateOurHandshakeMessage(ourKey, responderKey, groupIdOrEmpty, signData)
        val outboundMessage = generateLinkManagerMessage(
            payload,
            sessionInfo.responderId.toAvroHoldingIdentity(),
            networkMap
        )
        queuedMessages.add(outboundMessage)
    }

    fun processResponderHandshake(message: ResponderHandshakeMessage) {
        val (sessionInfo, session) = pendingSessions[message.header.sessionId] ?: run {
            logger.warn("Received ${message::class.java::getSimpleName} with sessionId = ${message.header.sessionId} " +
                "but there is no pending session. The message was discarded.")
            return
        }

        val responderKey = networkMap.getPublicKey(sessionInfo.responderId)
        if (responderKey == null) {
            logger.warn("Received ${ResponderHandshakeMessage::class.java.simpleName} from peer " +
                    "(${sessionInfo.responderId}) which is not in the network map. The message was discarded.")
            return
        }
        try {
            session.validatePeerHandshakeMessage(message, responderKey)
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.warn("Received ${ResponderHandshakeMessage::class.java.simpleName} from peer " +
                "${sessionInfo.responderId} with sessionId = ${message.header.sessionId} which failed validation. " +
                 "The message was discarded.")
            return
        }

        activeSessions[sessionInfo] = session.getSession()
        pendingSessions.remove(message.header.sessionId)
        queuedMessagesPendingSession[sessionInfo]?.forEach { sendMessage(it) }
    }
}