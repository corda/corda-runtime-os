package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.GatewayToLinkManagerMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.Step2Message
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import java.util.concurrent.ConcurrentHashMap
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.createLinkManagerToGatewayMessage
import net.corda.p2p.linkmanager.sessions.LinkManagerNetworkMap.Companion.toAvroHoldingIdentity
import java.util.*
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class SessionManager(
    private val mode: ProtocolMode,
    private val networkMap: LinkManagerNetworkMap,
    private val maxMessageSize: Int,
    private val sessionNegotiatedCallback: (SessionKey, AuthenticatedSession, LinkManagerNetworkMap) -> Unit
    ) {

    companion object {
        internal fun ByteBuffer.toByteArray(): ByteArray {
            return ByteArray(this.capacity()) { this.get(it) }
        }
    }

    //On the Initiator side there is a single unique session per SessionKey.
    data class SessionKey(val ourGroupId: String?, val responderId: LinkManagerNetworkMap.NetMapHoldingIdentity)

    private val pendingInitiatorSessions = ConcurrentHashMap<String, Pair<SessionKey, AuthenticationProtocolInitiator>>()
    private val activeInitiatorSessions = ConcurrentHashMap<SessionKey, AuthenticatedSession>()

    private val pendingResponderSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeResponderSessions = ConcurrentHashMap<String, AuthenticatedSession>()

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    fun getInitiatorSession(key: SessionKey): AuthenticatedSession? {
        return activeInitiatorSessions[key]
    }

    fun getResponderSession(uuid: String): AuthenticatedSession? {
        return activeResponderSessions[uuid]
    }

    fun processSessionMessage(message: GatewayToLinkManagerMessage): LinkManagerToGatewayMessage? {
        return when(val payload = message.payload) {
            is ResponderHelloMessage -> processResponderHello(payload)
            is ResponderHandshakeMessage -> processResponderHandshake(payload)
            is InitiatorHandshakeMessage -> processInitiatorHandshake(payload)
            is Step2Message -> processStep2Message(payload)
            else -> {
                logger.warn("Cannot process message of type: ${payload::class.java}.")
                null
            }
        }
    }

    fun beginSessionNegotiation(sessionKey: SessionKey): LinkManagerToGatewayMessage {
        val sessionId = UUID.randomUUID().toString()
        val session = AuthenticationProtocolInitiator(sessionId, listOf(mode), maxMessageSize)
        pendingInitiatorSessions[sessionId] = Pair(sessionKey, session)
        return createLinkManagerToGatewayMessage(
            session.generateInitiatorHello(),
            sessionKey.responderId.toAvroHoldingIdentity(),
            networkMap
        )
    }

    private fun processResponderHello(message: ResponderHelloMessage): LinkManagerToGatewayMessage? {
        val (sessionInfo, session) = pendingInitiatorSessions[message.header.sessionId] ?: run {
            logger.warn("Received ${message::class.java::getSimpleName} with sessionId ${message.header.sessionId} " +
                    "but there is no pending session. The message was discarded.")
            return null
        }

        session.receiveResponderHello(message)
        session.generateHandshakeSecrets()
        val ourKey = networkMap.getOurPublicKey(sessionInfo.ourGroupId)
        if (ourKey == null) {
            logger.warn("Cannot find public key for our Holding Identity ${sessionInfo.ourGroupId}.")
            return null
        }

        val responderKey = networkMap.getPublicKey(sessionInfo.responderId)
        if (responderKey == null) {
            logger.warn("Received ${ResponderHelloMessage::class.java.simpleName} from peer " +
                "(${sessionInfo}) which is not in the network map. The message was discarded.")
            return null
        }
        val signData = {it : ByteArray -> (networkMap::signData)(sessionInfo.ourGroupId, it)}
        val groupIdOrEmpty = sessionInfo.ourGroupId ?: ""
        val payload = session.generateOurHandshakeMessage(ourKey, responderKey, groupIdOrEmpty, signData)
        return createLinkManagerToGatewayMessage(
            payload,
            sessionInfo.responderId.toAvroHoldingIdentity(),
            networkMap
        )
    }

    private fun processResponderHandshake(message: ResponderHandshakeMessage): LinkManagerToGatewayMessage? {
        val (sessionInfo, session) = pendingInitiatorSessions[message.header.sessionId] ?: run {
            logger.warn("Received ${message::class.java::getSimpleName} with sessionId = ${message.header.sessionId} " +
                "but there is no pending session. The message was discarded.")
            return null
        }

        val responderKey = networkMap.getPublicKey(sessionInfo.responderId)
        if (responderKey == null) {
            logger.warn("Received ${ResponderHandshakeMessage::class.java.simpleName} from peer " +
                    "(${sessionInfo.responderId}) which is not in the network map. The message was discarded.")
            return null
        }
        try {
            session.validatePeerHandshakeMessage(message, responderKey)
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.warn("Received ${ResponderHandshakeMessage::class.java.simpleName} from peer " +
                "${sessionInfo.responderId} with sessionId = ${message.header.sessionId} which failed validation. " +
                 "The message was discarded.")
            return null
        }
        val authenticatedSession = session.getSession()
        activeInitiatorSessions[sessionInfo] = authenticatedSession
        pendingInitiatorSessions.remove(message.header.sessionId)
        sessionNegotiatedCallback(sessionInfo, authenticatedSession, networkMap)
        return null
    }

    private fun processStep2Message(message: Step2Message): LinkManagerToGatewayMessage? {
        val session = AuthenticationProtocolResponder.fromStep2(message.initiatorHello.header.sessionId,
            listOf(ProtocolMode.AUTHENTICATION_ONLY),
            maxMessageSize,
            message.initiatorHello,
            message.responderHello,
            message.privateKey.toByteArray(),
            message.publicKey.toByteArray())
        session.generateHandshakeSecrets()
        pendingResponderSessions[message.initiatorHello.header.sessionId] = session
        return null
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage): LinkManagerToGatewayMessage? {
        val session = pendingResponderSessions[message.header.sessionId]
        if (session == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                    "${message.header.sessionId} but there is no pending session with this id. The message was discarded.")
            return null
        }

        val identityData = session.validatePeerHandshakeMessage(message, networkMap::getPublicKeyFromHash)
        //Find the correct Holding Identity to use (using the public key hash).
        val us = networkMap.getPeerFromHash(identityData.responderPublicKeyHash)
        if (us == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                    "${message.header.sessionId}. Our identity (responder) with public key hash = " +
                    "${identityData.initiatorPublicKeyHash} is not in the network map.")
            return null
        }

        val ourPublicKey = networkMap.getOurPublicKey(us.groupId)
        if (ourPublicKey == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} from peer. Our key (for " +
                    "${us.groupId} is not in the networkMap.")
            return null
        }

        val signData = {it : ByteArray -> (networkMap::signData)(us.groupId, it)}
        val response = session.generateOurHandshakeMessage(ourPublicKey, signData)
        val peer = networkMap.getPeerFromHash(identityData.initiatorPublicKeyHash)?.toAvroHoldingIdentity()
        if (peer == null) {
            logger.warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId = " +
                    "${message.header.sessionId}. Initiator identity with public key hash = " +
                    "${identityData.initiatorPublicKeyHash} is not in the network map.")
            return null
        }

        activeResponderSessions[message.header.sessionId] = session.getSession()
        pendingResponderSessions.remove(message.header.sessionId)
        return createLinkManagerToGatewayMessage(response, peer, networkMap)
    }
}