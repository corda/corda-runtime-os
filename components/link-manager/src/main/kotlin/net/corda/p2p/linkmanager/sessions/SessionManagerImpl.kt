package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
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
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.NoPublicKeyForHashException
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.couldNotFindNetworkType
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourIdNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerNotInTheNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.validationFailedWarning
import org.slf4j.LoggerFactory
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
        fun getSessionKeyFromMessage(message: AuthenticatedMessage): SessionKey {
           val peer = message.header.destination.toHoldingIdentity()
           val us = message.header.source.toHoldingIdentity()
           return SessionKey(us, peer)
       }
    }

    //On the Outbound side there is a single unique session per SessionKey.
    data class SessionKey(
        val ourId: LinkManagerNetworkMap.HoldingIdentity,
        val responderId: LinkManagerNetworkMap.HoldingIdentity
    )

    private val pendingOutboundSessions = ConcurrentHashMap<String, Pair<SessionKey, AuthenticationProtocolInitiator>>()
    private val activeOutboundSessions = ConcurrentHashMap<SessionKey, Session>()
    private val activeOutboundSessionsById = ConcurrentHashMap<String, Session>()

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Session>()

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    private val sessionNegotiationLock = ReentrantLock()

    override fun processOutboundFlowMessage(message: AuthenticatedMessageAndKey): SessionState {
        sessionNegotiationLock.withLock {
            val key = getSessionKeyFromMessage(message.message)

            val activeSession = activeOutboundSessions[key]
            if (activeSession != null) {
                return SessionState.SessionEstablished(activeSession)
            }
            if (pendingOutboundSessionMessageQueues.queueMessage(message, key)) {
                val (sessionId, initMessage) = getSessionInitMessage(key) ?: return SessionState.CannotEstablishSession
                return SessionState.NewSessionNeeded(sessionId, initMessage)
            } else {
                return SessionState.SessionAlreadyPending
            }
        }
    }

    override fun getSessionById(uuid: String): SessionManager.SessionDirection {
        val inboundSession = activeInboundSessions[uuid]
        if (inboundSession != null) {
            return SessionManager.SessionDirection.Inbound(inboundSession)
        }
        val outboundSession = activeOutboundSessionsById[uuid]
        return if (outboundSession != null) {
            SessionManager.SessionDirection.Outbound(outboundSession)
        } else {
            SessionManager.SessionDirection.NoSession
        }
    }

    override fun processSessionMessage(message: LinkInMessage): LinkOutMessage? {
        return when(val payload = message.payload) {
            is ResponderHelloMessage -> processResponderHello(payload)
            is ResponderHandshakeMessage -> processResponderHandshake(payload)
            is InitiatorHelloMessage -> processInitiatorHello(payload)
            is InitiatorHandshakeMessage -> processInitiatorHandshake(payload)
            else -> {
                logger.warn("Cannot process message of type: ${payload::class.java}. The message was discarded.")
                null
            }
        }
    }

    private fun getSessionInitMessage(sessionKey: SessionKey): Pair<String, LinkOutMessage>? {
        val sessionId = UUID.randomUUID().toString()
        val networkType = networkMap.getNetworkType(sessionKey.ourId.groupId)
        if (networkType == null) {
            logger.warn("Could not find the network type in the NetworkMap for groupId ${sessionKey.ourId.groupId}." +
                " The sessionInit message was not sent.")
            return null
        }
        val ourMemberInfo = networkMap.getMemberInfo(sessionKey.ourId)
        if (ourMemberInfo == null) {
            logger.warn("Attempted to start session negotiation with peer ${sessionKey.responderId} but our identity ${sessionKey.ourId}" +
                " is not in the network map. The sessionInit message was not sent.")
            return null
        }

        val responderMemberInfo = networkMap.getMemberInfo(sessionKey.responderId)
        if (responderMemberInfo == null) {
            logger.warn("Attempted to start session negotiation with peer ${sessionKey.responderId} which is not in the network map. " +
                    "The sessionInit message was not sent.")
            return null
        }
        val session = AuthenticationProtocolInitiator(
            sessionId,
            supportedModes,
            maxMessageSize,
            ourMemberInfo.publicKey,
            ourMemberInfo.holdingIdentity.groupId
        )
        pendingOutboundSessions[sessionId] = Pair(sessionKey, session)
        val message = createLinkOutMessage(
            session.generateInitiatorHello(),
            responderMemberInfo,
            networkType
        )
        return sessionId to message
    }

    private fun ByteArray.toBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

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

        val ourMemberInfo = networkMap.getMemberInfo(sessionInfo.ourId)
        if (ourMemberInfo == null) {
            logger.ourIdNotInNetworkMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.ourId)
            return null
        }

        val responderMemberInfo = networkMap.getMemberInfo(sessionInfo.responderId)
        if (responderMemberInfo == null) {
            logger.peerNotInTheNetworkMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.responderId)
            return null
        }

        val signWithOurGroupId = { data: ByteArray -> cryptoService.signData(ourMemberInfo.publicKey, data) }
        val payload = try {
            session.generateOurHandshakeMessage(
                responderMemberInfo.publicKey,
                signWithOurGroupId
            )
        } catch (exception: LinkManagerCryptoService.NoPrivateKeyForGroupException) {
            logger.warn("${exception.message}. The ${message::class.java.simpleName} with sessionId ${message.header.sessionId}" +
                " was discarded.")
            return null
        }

        val networkType = networkMap.getNetworkType(ourMemberInfo.holdingIdentity.groupId)
        if (networkType == null) {
            logger.couldNotFindNetworkType(message::class.java.simpleName, message.header.sessionId, ourMemberInfo.holdingIdentity.groupId)
            return null
        }
        return createLinkOutMessage(payload, responderMemberInfo, networkType)
    }

    private fun processResponderHandshake(message: ResponderHandshakeMessage): LinkOutMessage? {
        val (sessionInfo, session) = pendingOutboundSessions[message.header.sessionId] ?: run {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val responderKey = networkMap.getMemberInfo(sessionInfo.responderId)?.publicKey
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
            activeOutboundSessionsById[message.header.sessionId] = authenticatedSession
            pendingOutboundSessions.remove(message.header.sessionId)
            pendingOutboundSessionMessageQueues.sessionNegotiatedCallback(sessionInfo, authenticatedSession, networkMap)
        }
        return null
    }

    private fun processInitiatorHello(message: InitiatorHelloMessage): LinkOutMessage? {
        if (pendingInboundSessions.containsKey(message.header.sessionId)) {
            logger.warn("Already received a ${InitiatorHelloMessage::class.java.simpleName} for ${message.header.sessionId}. " +
                    "The message was discarded.")
            return null
        }

        val session = AuthenticationProtocolResponder(message.header.sessionId, supportedModes, maxMessageSize)
        session.receiveInitiatorHello(message)
        val responderHello = session.generateResponderHello()
        val peer = networkMap.getMemberInfoFromPublicKeyHash(message.source.initiatorPublicKeyHash.array(), message.source.groupId)
        if (peer == null) {
            logger.peerHashNotInNetworkMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                message.source.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }
        val networkType = networkMap.getNetworkType(peer.holdingIdentity.groupId)
        if (networkType == null) {
            logger.couldNotFindNetworkType(message::class.java.simpleName, message.header.sessionId, peer.holdingIdentity.groupId)
            return null
        }
        pendingInboundSessions[message.header.sessionId] = session
        return createLinkOutMessage(responderHello, peer, networkType)
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage): LinkOutMessage? {
        val session = pendingInboundSessions[message.header.sessionId]
        if (session == null) {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        session.generateHandshakeSecrets()
        val identityData = try {
            session.validatePeerHandshakeMessage(message, networkMap::getPublicKeyFromHash)
        } catch (exception: NoPublicKeyForHashException) {
            logger.warn("Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                " The message was discarded.")
            return null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        }

        //Find the correct Holding Identity to use (using the public key hash).
        val ourMemberInfo = networkMap.getMemberInfoFromPublicKeyHash(identityData.responderPublicKeyHash, identityData.groupId)
        if (ourMemberInfo == null) {
            logger.ourHashNotInNetworkMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                identityData.responderPublicKeyHash.toBase64()
            )
            return null
        }

        val ourPublicKey = ourMemberInfo.publicKey

        val signData = {data: ByteArray -> cryptoService.signData(ourMemberInfo.publicKey, data)}
        val response = try {
            session.generateOurHandshakeMessage(ourPublicKey, signData)
        } catch (exception: LinkManagerCryptoService.NoPrivateKeyForGroupException) {
            logger.warn("Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                    " The message was discarded.")
            return null
        }

        val peer = networkMap.getMemberInfoFromPublicKeyHash(identityData.initiatorPublicKeyHash, identityData.groupId)
        if (peer == null) {
            logger.peerHashNotInNetworkMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                identityData.initiatorPublicKeyHash.toBase64()
            )
            return null
        }

        val networkType = networkMap.getNetworkType(ourMemberInfo.holdingIdentity.groupId)
        if (networkType == null) {
            logger.couldNotFindNetworkType(message::class.java.simpleName, message.header.sessionId, ourMemberInfo.holdingIdentity.groupId)
            return null
        }

        activeInboundSessions[message.header.sessionId] = session.getSession()
        pendingInboundSessions.remove(message.header.sessionId)

        return createLinkOutMessage(response, peer, networkType)
    }
}