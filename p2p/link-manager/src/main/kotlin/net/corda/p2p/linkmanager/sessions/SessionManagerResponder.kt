package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.Step2Message
import net.corda.p2p.linkmanager.sessions.SessionManagerInitiator.Companion.generateLinkManagerMessage
import net.corda.p2p.linkmanager.sessions.SessionManagerInitiator.Companion.toByteArray
import net.corda.p2p.linkmanager.sessions.SessionNetworkMap.Companion.toAvroPeer
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class SessionManagerResponder(
    private val mode: ProtocolMode,
    private val networkMap: SessionNetworkMap
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java.name)
    private val queuedOutboundMessages = ConcurrentLinkedQueue<LinkManagerToGatewayMessage>()
    private val queuedInboundMessages =  ConcurrentLinkedQueue<FlowMessage>()
    private val pendingSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeSessions = ConcurrentHashMap<String, AuthenticatedSession>()

    fun processMessage(message: Any) {
        when (message) {
            is AuthenticatedDataMessage -> processAuthenticatedMessage(message)
            is InitiatorHandshakeMessage -> processInitiatorHandshake(message)
            is Step2Message -> processStep2Message(message)
        }
    }

    fun getQueuedOutboundMessage(): LinkManagerToGatewayMessage? {
        return queuedOutboundMessages.poll()
    }

    fun getQueuedInboundMessage(): FlowMessage? {
        return queuedInboundMessages.poll()
    }

    private fun processAuthenticatedMessage(message: AuthenticatedDataMessage) {
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

    private fun processStep2Message(message: Step2Message) {
        val session = AuthenticationProtocolResponder.fromStep2(message.initiatorHello.header.sessionId,
            listOf(ProtocolMode.AUTHENTICATION_ONLY),
            message.initiatorHello,
            message.responderHello,
            message.privateKey.toByteArray(),
            message.publicKey.toByteArray())
        session.generateHandshakeSecrets()
        pendingSessions[message.initiatorHello.header.sessionId] = session
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage) {
        val session = pendingSessions[message.header.sessionId]
        if (session == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                "${message.header.sessionId} but there is no pending session with this id. The message was discarded.")
            return
        }
        val ourKey = networkMap.getOurPublicKey()
        val identityData = session.validatePeerHandshakeMessage(message, networkMap::getPublicKeyFromHash)
        val response = session.generateOurHandshakeMessage(ourKey, networkMap::signData)
        val peer = networkMap.getPeerFromHash(identityData.initiatorPublicKeyHash)?.toAvroPeer()
        if (peer == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                "${message.header.sessionId}. Identity with public key hash = ${identityData.initiatorPublicKeyHash} " +
                "is not in the network map.")
            return
        }
        val responseMessage = generateLinkManagerMessage(response, peer, networkMap)
        queuedOutboundMessages.add(responseMessage)
        activeSessions[message.header.sessionId] = session.getSession()
        pendingSessions.remove(message.header.sessionId)
    }
}