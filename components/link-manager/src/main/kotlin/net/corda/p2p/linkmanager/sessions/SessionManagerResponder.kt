package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.GatewayToLinkManagerMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.Step2Message
import net.corda.p2p.linkmanager.sessions.SessionManagerInitiator.Companion.generateLinkManagerMessage
import net.corda.p2p.linkmanager.sessions.SessionManagerInitiator.Companion.toByteArray
import net.corda.p2p.linkmanager.sessions.SessionNetworkMap.Companion.toAvroHoldingIdentity
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class SessionManagerResponder(
    private val mode: ProtocolMode,
    private val networkMap: SessionNetworkMap,
    private val maxMessageSize: Int
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java.name)
    private val queuedOutboundMessages = ConcurrentLinkedQueue<LinkManagerToGatewayMessage>()
    private val queuedInboundMessages =  ConcurrentLinkedQueue<FlowMessage>()
    private val pendingSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeSessions = ConcurrentHashMap<String, AuthenticatedSession>()

    fun processMessage(message: GatewayToLinkManagerMessage) {
        when (val payload = message.payload) {
            is AuthenticatedDataMessage -> processAuthenticatedMessage(payload)
            is InitiatorHandshakeMessage -> processInitiatorHandshake(payload)
            is Step2Message -> processStep2Message(payload)
        }
    }

    fun getQueuedOutboundMessage(): LinkManagerToGatewayMessage? {
        return queuedOutboundMessages.poll()
    }

    fun getQueuedOutboundMessages(): List<LinkManagerToGatewayMessage> {
        val messages = mutableListOf<LinkManagerToGatewayMessage>()
        for (i in 0 until queuedOutboundMessages.size) {
            val message = queuedOutboundMessages.element() ?: break
            messages.add(message)
        }
        return messages
    }

    fun getQueuedInboundMessage(): FlowMessage? {
        return queuedInboundMessages.poll()
    }

    fun getQueuedInboundMessages(): List<FlowMessage> {
        val messages = mutableListOf<FlowMessage>()
        for (i in 0 until queuedInboundMessages.size) {
            val message = queuedInboundMessages.element() ?: break
            messages.add(message)
        }
        return messages
    }


    fun processAuthenticatedMessage(message: AuthenticatedDataMessage) {
        val session = activeSessions[message.header.sessionId]
        if (session != null) {
            try {
                session.validateMac(message.header, message.payload.array(), message.authTag.array())
            } catch (exception: InvalidMac) {
                logger.warn("MAC check failed for message for session ${message.header.sessionId}.")
                return
            }
            val deserializedMessage = try {
                FlowMessage.fromByteBuffer(message.payload)
            } catch (exception: IOException) {
                logger.warn("Could not deserialize message for session ${message.header.sessionId}.")
                return
            }
            queuedInboundMessages.add(deserializedMessage)
        } else {
            logger.warn("Received message of type ${AuthenticatedDataMessage::class.java.simpleName}" +
                    " but there is no session with id: ${message.header.sessionId}")
        }
    }

    fun processStep2Message(message: Step2Message) {
        val session = AuthenticationProtocolResponder.fromStep2(message.initiatorHello.header.sessionId,
            listOf(ProtocolMode.AUTHENTICATION_ONLY),
            maxMessageSize,
            message.initiatorHello,
            message.responderHello,
            message.privateKey.toByteArray(),
            message.publicKey.toByteArray())
        session.generateHandshakeSecrets()
        pendingSessions[message.initiatorHello.header.sessionId] = session
    }

    fun processInitiatorHandshake(message: InitiatorHandshakeMessage) {
        val session = pendingSessions[message.header.sessionId]
        if (session == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                "${message.header.sessionId} but there is no pending session with this id. The message was discarded.")
            return
        }

        val identityData = session.validatePeerHandshakeMessage(message, networkMap::getPublicKeyFromHash)
        //Find the correct Holding Identity to use (using the public key hash).
        val us = networkMap.getPeerFromHash(identityData.responderPublicKeyHash)
        if (us == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                "${message.header.sessionId}. Our identity (responder) with public key hash = " +
                 "${identityData.initiatorPublicKeyHash} is not in the network map.")
            return
        }

        val ourPublicKey = networkMap.getOurPublicKey(us.groupId)
        if (ourPublicKey == null) {
            logger.info("Received ${InitiatorHandshakeMessage::class.java.simpleName} from peer. Our key (for " +
                "${us.groupId} is not in the networkMap.")
            return
        }

        val signData = {it : ByteArray -> (networkMap::signData)(us.groupId, it)}
        val response = session.generateOurHandshakeMessage(ourPublicKey, signData)
        val peer = networkMap.getPeerFromHash(identityData.initiatorPublicKeyHash)?.toAvroHoldingIdentity()
        if (peer == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                "${message.header.sessionId}. Initiator identity with public key hash = " +
                "${identityData.initiatorPublicKeyHash} is not in the network map.")
            return
        }
        val responseMessage = generateLinkManagerMessage(response, peer, networkMap)
        queuedOutboundMessages.add(responseMessage)
        activeSessions[message.header.sessionId] = session.getSession()
        pendingSessions.remove(message.header.sessionId)
    }
}