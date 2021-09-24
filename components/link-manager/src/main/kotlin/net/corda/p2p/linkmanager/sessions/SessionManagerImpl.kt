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
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.delivery.SessionReplayer
import net.corda.p2p.linkmanager.delivery.SessionReplayer.SessionMessageReplay
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.couldNotFindNetworkType
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.couldNotFindNetworkTypeOnReplay
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourHashNotInNetworkOnReplay
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourIdNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerHashNotInNetworkMapOnReplay
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerNotInTheNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.validationFailedWarning
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class SessionManagerImpl(
    private val sessionNegotiationParameters: ParametersForSessionNegotiation,
    private val networkMap: LinkManagerNetworkMap,
    private val cryptoService: LinkManagerCryptoService,
    private val pendingOutboundSessionMessageQueues: LinkManager.PendingSessionMessageQueues,
    private val sessionReplayer: SessionReplayer
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

    /**
     * The set of parameters negotiated during Session Negotiation.
     */
    data class ParametersForSessionNegotiation(
        val maxMessageSize: Int,
        val supportedModes: Set<ProtocolMode>
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

    override fun inboundSessionEstablished(sessionId: String) {
        pendingInboundSessions.remove(sessionId)
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

        val session = AuthenticationProtocolInitiator(
            sessionId,
            sessionNegotiationParameters.supportedModes,
            sessionNegotiationParameters.maxMessageSize,
            ourMemberInfo.publicKey,
            ourMemberInfo.holdingIdentity.groupId
        )

        pendingOutboundSessions[sessionId] = Pair(sessionKey, session)

        val sessionInitPayload = session.generateInitiatorHello()
        sessionReplayer.addMessageForReplay(
            sessionId + "_" + sessionInitPayload::class.java.simpleName,
            SessionMessageReplay(sessionInitPayload, sessionKey.responderId)
        )

        val responderMemberInfo = networkMap.getMemberInfo(sessionKey.responderId)
        if (responderMemberInfo == null) {
            logger.warn("Attempted to start session negotiation with peer ${sessionKey.responderId} which is not in the network map. " +
                    "The sessionInit message was not sent.")
            return null
        }

        val message = createLinkOutMessage(sessionInitPayload, responderMemberInfo, networkType)
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
        sessionReplayer.removeMessageFromReplay(message.header.sessionId + "_" + InitiatorHelloMessage::class.java.simpleName)
        sessionReplayer.addMessageForReplay(
            message.header.sessionId + "_" + payload::class.java.simpleName,
            SessionMessageReplay(payload, sessionInfo.responderId)
        )

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

        val memberInfo = networkMap.getMemberInfo(sessionInfo.responderId)
        if (memberInfo == null) {
            logger.peerNotInTheNetworkMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.responderId)
            return null
        }

        try {
            session.validatePeerHandshakeMessage(message, memberInfo.publicKey, memberInfo.publicKeyAlgorithm)
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        }
        val authenticatedSession = session.getSession()
        sessionReplayer.removeMessageFromReplay(message.header.sessionId + "_" + InitiatorHandshakeMessage::class.java.simpleName)
        sessionNegotiationLock.withLock {
            activeOutboundSessions[sessionInfo] = authenticatedSession
            activeOutboundSessionsById[message.header.sessionId] = authenticatedSession
            pendingOutboundSessions.remove(message.header.sessionId)
            pendingOutboundSessionMessageQueues.sessionNegotiatedCallback(sessionInfo, authenticatedSession, networkMap)
        }
        return null
    }

    private fun processInitiatorHello(message: InitiatorHelloMessage): LinkOutMessage? {
        val session = pendingInboundSessions.computeIfAbsent(message.header.sessionId) { sessionId ->
            val session = AuthenticationProtocolResponder(
                sessionId,
                sessionNegotiationParameters.supportedModes,
                sessionNegotiationParameters.maxMessageSize
            )
            session.receiveInitiatorHello(message)
            session
        }
        val responderHello = session.generateResponderHello()

        val peer = networkMap.getMemberInfo(message.source.initiatorPublicKeyHash.array(), message.source.groupId)
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
        return createLinkOutMessage(responderHello, peer, networkType)
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage): LinkOutMessage? {
        val session = pendingInboundSessions[message.header.sessionId]
        if (session == null) {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val initiatorIdentityData = session.getInitiatorIdentity()
        val peer = networkMap.getMemberInfo(initiatorIdentityData.initiatorPublicKeyHash.array(), initiatorIdentityData.groupId)
        if (peer == null) {
            logger.peerHashNotInNetworkMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                initiatorIdentityData.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }

        session.generateHandshakeSecrets()
        val ourIdentityData = try {
            session.validatePeerHandshakeMessage(message, peer.publicKey, peer.publicKeyAlgorithm)
        } catch (exception: WrongPublicKeyHashException) {
            logger.error(exception.message)
            return null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                exception.message
            )
            return null
        }
        //Find the correct Holding Identity to use (using the public key hash).
        val ourMemberInfo = networkMap.getMemberInfo(ourIdentityData.responderPublicKeyHash, ourIdentityData.groupId)
        if (ourMemberInfo == null) {
            logger.ourHashNotInNetworkMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                ourIdentityData.responderPublicKeyHash.toBase64()
            )
            return null
        }

        val networkType = networkMap.getNetworkType(ourMemberInfo.holdingIdentity.groupId)
        if (networkType == null) {
            logger.couldNotFindNetworkType(message::class.java.simpleName, message.header.sessionId, ourMemberInfo.holdingIdentity.groupId)
            return null
        }

        val response = try {
            val ourPublicKey = ourMemberInfo.publicKey
            val signData = {data: ByteArray -> cryptoService.signData(ourMemberInfo.publicKey, data)}
            session.generateOurHandshakeMessage(ourPublicKey, signData)
        } catch (exception: LinkManagerCryptoService.NoPrivateKeyForGroupException) {
            logger.warn("Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                    " The message was discarded.")
            return null
        }

        activeInboundSessions[message.header.sessionId] = session.getSession()
        /**
         * We delay removing the session from pendingInboundSessions only after we receive the first data message as before this point
         * the other side (Initiator) might replay [InitiatorHandshakeMessage] in the case where the [ResponderHandshakeMessage] was lost.
         * */
        return createLinkOutMessage(response, peer, networkType)
    }
}