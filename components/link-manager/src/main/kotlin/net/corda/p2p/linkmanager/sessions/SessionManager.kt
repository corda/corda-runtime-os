package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.groupIdNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.hashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.initiatorHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerNotInTheNetworkMapWarning
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SessionManager(
    private val supportedModes: Set<ProtocolMode>,
    private val networkMap: LinkManagerNetworkMap,
    private val cryptoService: LinkManagerCryptoService,
    private val maxMessageSize: Int,
    private val sessionNegotiatedCallback: (SessionKey, Session, LinkManagerNetworkMap) -> Unit
    ) {

    //On the Initiator side there is a single unique session per SessionKey.
    data class SessionKey(val ourGroupId: String?, val responderId: LinkManagerNetworkMap.HoldingIdentity)

    private val pendingInitiatorSessions = ConcurrentHashMap<String, Pair<SessionKey, AuthenticationProtocolInitiator>>()
    private val activeInitiatorSessions = ConcurrentHashMap<SessionKey, Session>()

    private val pendingResponderSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeResponderSessions = ConcurrentHashMap<String, Session>()

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    @VisibleForTesting
    fun setLogger(newLogger: Logger) {
        logger = newLogger
    }

    fun getInitiatorSession(key: SessionKey): Session? {
        return activeInitiatorSessions[key]
    }

    fun getResponderSession(uuid: String): Session? {
        return activeResponderSessions[uuid]
    }

    fun processSessionMessage(message: LinkInMessage): LinkOutMessage? {
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

    fun getSessionInitMessage(sessionKey: SessionKey): LinkOutMessage? {
        val sessionId = UUID.randomUUID().toString()
        val session = AuthenticationProtocolInitiator(sessionId, supportedModes, maxMessageSize)
        pendingInitiatorSessions[sessionId] = Pair(sessionKey, session)
        return createLinkOutMessage(
            session.generateInitiatorHello(),
            sessionKey.responderId.toHoldingIdentity(),
            networkMap
        )
    }

    private fun signData(groupId: String?, data: ByteArray): ByteArray {
        val privateKey = networkMap.getOurPrivateKey(groupId) ?: throw NoPrivateKeyForGroupExceptions(groupId)
        return cryptoService.signData(privateKey, data)
    }

    private fun ByteArray.toBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    private fun getPublicKeyFromHash(hash: ByteArray): PublicKey {
        return networkMap.getPublicKeyFromHash(hash) ?: throw NoPublicKeyForHash(hash.toBase64())
    }

    class NoPrivateKeyForGroupExceptions(groupId: String?):
        CordaRuntimeException("Could not find (our) private key in the network map for group = $groupId")

    class NoPublicKeyForHash(hash: String):
        CordaRuntimeException("Could not find the public key in the network map by hash = $hash")

    private fun processResponderHello(message: ResponderHelloMessage): LinkOutMessage? {
        val (sessionInfo, session) = pendingInitiatorSessions[message.header.sessionId] ?: run {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
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

        val signWithOurGroupId = { data: ByteArray -> signData(sessionInfo.ourGroupId, data) }
        val groupIdOrEmpty = sessionInfo.ourGroupId ?: ""
        val payload = try {
            session.generateOurHandshakeMessage(ourKey, responderKey, groupIdOrEmpty, signWithOurGroupId)
        } catch (exception: NoPrivateKeyForGroupExceptions) {
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
        val (sessionInfo, session) = pendingInitiatorSessions[message.header.sessionId] ?: run {
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
            logger.warn("Received ${ResponderHandshakeMessage::class.java.simpleName} from peer " +
                "${sessionInfo.responderId} with sessionId ${message.header.sessionId} which failed validation. " +
                 "The message was discarded.")
            return null
        }
        val authenticatedSession = session.getSession()
        activeInitiatorSessions[sessionInfo] = authenticatedSession
        pendingInitiatorSessions.remove(message.header.sessionId)
        sessionNegotiatedCallback(sessionInfo, authenticatedSession, networkMap)
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
        pendingResponderSessions[message.initiatorHello.header.sessionId] = session
        return null
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage): LinkOutMessage? {
        val session = pendingResponderSessions[message.header.sessionId]
        if (session == null) {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val identityData = try {
            session.validatePeerHandshakeMessage(message, ::getPublicKeyFromHash)
        } catch (exception: NoPublicKeyForHash) {
            logger.warn("Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                    " The message was discarded.")
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

        val signData = {data: ByteArray -> signData(us.groupId, data)}
        val response = try {
            session.generateOurHandshakeMessage(ourPublicKey, signData)
        } catch (exception: NoPrivateKeyForGroupExceptions) {
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

        activeResponderSessions[message.header.sessionId] = session.getSession()
        pendingResponderSessions.remove(message.header.sessionId)
        return createLinkOutMessage(response, peer, networkMap)
    }
}