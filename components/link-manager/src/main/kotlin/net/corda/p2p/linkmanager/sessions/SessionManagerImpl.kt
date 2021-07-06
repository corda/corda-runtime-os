package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.FlowMessage
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.groupIdNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.hashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.initiatorHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerNotInTheNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.validationFailedWarning
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class SessionManagerImpl(
    private val supportedModes: Set<ProtocolMode>,
    private val networkMap: LinkManagerNetworkMap,
    private val cryptoService: LinkManagerCryptoService,
    private val maxMessageSize: Int,
    private val pendingOutboundSessionMessageQueues: LinkManager.PendingSessionMessageQueues
    ): SessionManager {

    companion object {
        fun getSessionKeyFromMessage(message: FlowMessage): SessionKey? {
           val peer = message.header.destination.toHoldingIdentity() ?: return null
           val us = message.header.source.toHoldingIdentity() ?: return null
           return SessionKey(us.groupId, us.type, peer)
       }
    }

    //On the Outbound side there is a single unique session per SessionKey.
    data class SessionKey(
        val ourGroupId: String,
        val ourType: LinkManagerNetworkMap.IdentityType,
        val responderId: LinkManagerNetworkMap.HoldingIdentity
    )

    private val pendingOutboundSessions = ConcurrentHashMap<String, Pair<SessionKey, AuthenticationProtocolInitiator>>()
    private val activeOutboundSessions = ConcurrentHashMap<SessionKey, Session>()

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Session>()

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    private val sessionNegotiationLock = ReentrantLock()

    @VisibleForTesting
    override fun setLogger(newLogger: Logger) {
        logger = newLogger
    }

    override fun processOutboundFlowMessage(message: FlowMessage): SessionState {
        sessionNegotiationLock.withLock {
            val key = getSessionKeyFromMessage(message)
            if (key == null) {
                logger.error("Invalid identity read from ${FlowMessage::class.java.simpleName}. The message was discarded.")
                return SessionState.CannotEstablishSession
            }

            val activeSession = activeOutboundSessions[key]
            if (activeSession != null) {
                return SessionState.SessionEstablished(activeSession)
            }
            return if (pendingOutboundSessionMessageQueues.queueMessage(message, key)) {
                val (sessionId, initMessage) = getSessionInitMessage(key)
                if (initMessage == null) {
                    SessionState.CannotEstablishSession
                } else {
                    SessionState.NewSessionNeeded(sessionId, initMessage)
                }
            } else {
                SessionState.SessionAlreadyPending
            }
        }
    }

    override fun getInboundSession(uuid: String): Session? {
        return activeInboundSessions[uuid]
    }

    override fun processSessionMessage(message: LinkInMessage): LinkOutMessage? {
        return when(val payload = message.payload) {
            is ResponderHelloMessage -> processResponderHello(payload)
            is ResponderHandshakeMessage -> processResponderHandshake(payload)
            is InitiatorHandshakeMessage -> processInitiatorHandshake(payload)
            is Step2Message -> processStep2Message(payload)
            else -> {
                logger.warn("Cannot process message of type: ${payload::class.java}. The message was discarded.")
                null
            }
        }
    }

    private fun getSessionInitMessage(sessionKey: SessionKey): Pair<String, LinkOutMessage?> {
        val sessionId = UUID.randomUUID().toString()
        val session = AuthenticationProtocolInitiator(sessionId, supportedModes, maxMessageSize)
        pendingOutboundSessions[sessionId] = Pair(sessionKey, session)
        return sessionId to createLinkOutMessage(
            session.generateInitiatorHello(),
            sessionKey.responderId.toHoldingIdentity(),
            networkMap
        )
    }

    private fun ByteArray.toBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    private fun getPublicKeyFromHash(hash: ByteArray): PublicKey {
        return networkMap.getPublicKeyFromHash(hash) ?: throw NoPublicKeyForHashException(hash.toBase64())
    }

    class NoPublicKeyForHashException(hash: String):
        CordaRuntimeException("Could not find the public key in the network map by hash = $hash")

    private fun processResponderHello(message: ResponderHelloMessage): LinkOutMessage? {
        val (sessionInfo, session) = pendingOutboundSessions[message.header.sessionId] ?: run {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }
        if (session.step != AuthenticationProtocolInitiator.Step.SENT_MY_DH_KEY) {
            logger.warn("Already received a ${ResponderHelloMessage::class.java.simpleName} for ${message.header.sessionId}. " +
                    "The message was discarded.")
            return null
        }
        session.receiveResponderHello(message)
        session.generateHandshakeSecrets()

        val ourKey = networkMap.getOurPublicKey(sessionInfo.ourGroupId)
        if (ourKey == null) {
            logger.groupIdNotInNetworkMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.ourGroupId)
            return null
        }

        val responderKey = networkMap.getPublicKey(sessionInfo.responderId)
        if (responderKey == null) {
            logger.peerNotInTheNetworkMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.responderId)
            return null
        }

        val signWithOurGroupId = { data: ByteArray -> cryptoService.signData(networkMap.hashPublicKey(ourKey), data) }
        val payload = try {
            session.generateOurHandshakeMessage(ourKey, responderKey, sessionInfo.ourGroupId, signWithOurGroupId)
        } catch (exception: LinkManagerCryptoService.NoPrivateKeyForGroupException) {
            logger.warn("${exception.message}. The ${message::class.java.simpleName} was discarded.")
            return null
        }

        return createLinkOutMessage(
            payload,
            sessionInfo.responderId.toHoldingIdentity(),
            networkMap
        )
    }

    private fun processResponderHandshake(message: ResponderHandshakeMessage): LinkOutMessage? {
        val (sessionInfo, session) = pendingOutboundSessions[message.header.sessionId] ?: run {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val responderKey = networkMap.getPublicKey(sessionInfo.responderId)
        if (responderKey == null) {
            logger.peerNotInTheNetworkMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.responderId)
            return null
        }
        try {
            session.validatePeerHandshakeMessage(message, responderKey)
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        }
        val authenticatedSession = session.getSession()
        sessionNegotiationLock.withLock {
            activeOutboundSessions[sessionInfo] = authenticatedSession
            pendingOutboundSessions.remove(message.header.sessionId)
            pendingOutboundSessionMessageQueues.sessionNegotiatedCallback(sessionInfo, authenticatedSession, networkMap)
        }
        return null
    }

    private fun processStep2Message(message: Step2Message): LinkOutMessage? {
        val session = AuthenticationProtocolResponder.fromStep2(message.initiatorHello.header.sessionId,
            supportedModes,
            maxMessageSize,
            message.initiatorHello,
            message.responderHello,
            message.privateKey.array(),
            message.responderHello.responderPublicKey.array())
        session.generateHandshakeSecrets()
        pendingInboundSessions[message.initiatorHello.header.sessionId] = session
        return null
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage): LinkOutMessage? {
        val session = pendingInboundSessions[message.header.sessionId]
        if (session == null) {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val identityData = try {
            session.validatePeerHandshakeMessage(message, ::getPublicKeyFromHash)
        } catch (exception: NoPublicKeyForHashException) {
            logger.warn("Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                " The message was discarded.")
            return null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        }

        //Find the correct Holding Identity to use (using the public key hash).
        val us = networkMap.getPeerFromHash(identityData.responderPublicKeyHash)
        if (us == null) {
            logger.hashNotInNetworkMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                identityData.responderPublicKeyHash.toBase64()
            )
            return null
        }

        val ourPublicKey = networkMap.getOurPublicKey(us.groupId)
        if (ourPublicKey == null) {
            logger.groupIdNotInNetworkMapWarning(message::class.java.simpleName, message.header.sessionId, us.groupId)
            return null
        }

        val signData = {data: ByteArray -> cryptoService.signData(identityData.responderPublicKeyHash, data)}
        val response = try {
            session.generateOurHandshakeMessage(ourPublicKey, signData)
        } catch (exception: LinkManagerCryptoService.NoPrivateKeyForGroupException) {
            logger.warn("Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                    " The message was discarded.")
            return null
        }

        val peer = networkMap.getPeerFromHash(identityData.initiatorPublicKeyHash)?.toHoldingIdentity()
        if (peer == null) {
            logger.initiatorHashNotInNetworkMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                identityData.initiatorPublicKeyHash.toBase64()
            )
            return null
        }

        activeInboundSessions[message.header.sessionId] = session.getSession()
        pendingInboundSessions.remove(message.header.sessionId)
        return createLinkOutMessage(response, peer, networkMap)
    }
}