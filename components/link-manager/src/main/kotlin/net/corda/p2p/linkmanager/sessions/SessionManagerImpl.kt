package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.HeartbeatMessage
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerConfig
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.delivery.SessionReplayer
import net.corda.p2p.linkmanager.delivery.SessionReplayer.SessionMessageReplay
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionKey
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.couldNotFindNetworkType
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourIdNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerNotInTheNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.validationFailedWarning
import net.corda.p2p.schema.Schema
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList", "TooManyFunctions")
open class SessionManagerImpl(
    private val networkMap: LinkManagerNetworkMap,
    private val cryptoService: LinkManagerCryptoService,
    private val pendingOutboundSessionMessageQueues: LinkManager.PendingSessionMessageQueues,
    private val sessionReplayer: SessionReplayer,
    private val protocolFactory: ProtocolFactory = CryptoProtocolFactory(),
    publisherFactory: PublisherFactory,
    private val config: LinkManagerConfig
    ): SessionManager {

    companion object {
        fun getSessionKeyFromMessage(message: AuthenticatedMessage): SessionKey {
           val peer = message.header.destination.toHoldingIdentity()
           val us = message.header.source.toHoldingIdentity()
           return SessionKey(us, peer)
       }
    }

    private val heartbeatManager = HeartbeatManager(
        publisherFactory,
        networkMap,
        Duration.ofSeconds(config.heartbeatMessagePeriodSecs),
        Duration.ofSeconds(config.sessionTimeoutSecs)
    )

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()

    private val pendingOutboundSessions = ConcurrentHashMap<String, Pair<SessionKey, AuthenticationProtocolInitiator>>()
    private val pendingOutboundSessionKeys = ConcurrentHashMap.newKeySet<SessionKey>()
    private val activeOutboundSessions = ConcurrentHashMap<SessionKey, Pair<String, Session>>()
    private val activeOutboundSessionsById = ConcurrentHashMap<String, Session>()

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Session>()

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    private val sessionNegotiationLock = ReentrantReadWriteLock()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.write {
            if (!running) {
                heartbeatManager.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.write {
            if (running) {
                heartbeatManager.stop()
                running = false
            }
        }
    }

    override fun processOutboundMessage(message: AuthenticatedMessageAndKey): SessionState {
        sessionNegotiationLock.read {
            val key = getSessionKeyFromMessage(message.message)

            val activeSession = activeOutboundSessions[key]?.second
            if (activeSession != null) {
                return SessionState.SessionEstablished(activeSession)
            }
            pendingOutboundSessionMessageQueues.queueMessage(message, key)
            if (pendingOutboundSessionKeys.contains(key)) {
                return SessionState.SessionAlreadyPending
            } else {
                val (sessionId, initMessage) = getSessionInitMessage(key) ?: return SessionState.CannotEstablishSession
                return SessionState.NewSessionNeeded(sessionId, initMessage)
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
        startStopLock.read {
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
    }

    override fun inboundSessionEstablished(sessionId: String) {
        pendingInboundSessions.remove(sessionId)
    }

    override fun messageSent(messageAndKey: AuthenticatedMessageAndKey, session: Session) {
        startStopLock.read {
            heartbeatManager.messageSent(
                messageAndKey.message.header.messageId,
                SessionKey(
                    messageAndKey.message.header.source.toHoldingIdentity(),
                    messageAndKey.message.header.destination.toHoldingIdentity()
                ),
                session,
            )
        }
    }

    override fun messageAcknowledged(messageId: String) {
        startStopLock.read {
            heartbeatManager.messageAcknowledged(messageId)
        }
    }

    private fun destroyOutboundSession(sessionKey: SessionKey, sessionId: String) {
        sessionNegotiationLock.write {
            activeOutboundSessions.remove(sessionKey)
            pendingOutboundSessions.remove(sessionId)
            pendingOutboundSessionKeys.remove(sessionKey)
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

        val session = protocolFactory.createInitiator(
            sessionId,
            config.protocolModes,
            config.maxMessageSize,
            ourMemberInfo.publicKey,
            ourMemberInfo.holdingIdentity.groupId
        )

        pendingOutboundSessionKeys.add(sessionKey)
        pendingOutboundSessions[sessionId] = Pair(sessionKey, session)

        val sessionInitPayload = session.generateInitiatorHello()
        val initiatorHelloUniqueId = "${sessionId}_${sessionInitPayload::class.java.simpleName}"
        sessionReplayer.addMessageForReplay(
            initiatorHelloUniqueId,
            SessionMessageReplay(sessionInitPayload, sessionKey.responderId)
        )

        val responderMemberInfo = networkMap.getMemberInfo(sessionKey.responderId)
        if (responderMemberInfo == null) {
            logger.warn("Attempted to start session negotiation with peer ${sessionKey.responderId} which is not in the network map. " +
                    "The sessionInit message was not sent.")
            return null
        }
        heartbeatManager.sessionMessageSent(initiatorHelloUniqueId, sessionKey, sessionId, ::destroyOutboundSession)

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

        val initiatorHelloUniqueId = "${message.header.sessionId}_${InitiatorHelloMessage::class.java.simpleName}"
        sessionReplayer.removeMessageFromReplay(initiatorHelloUniqueId)
        heartbeatManager.messageAcknowledged(initiatorHelloUniqueId)

        val initiatorHandshakeUniqueId = "${message.header.sessionId}_${payload::class.java.simpleName}"
        sessionReplayer.addMessageForReplay(initiatorHandshakeUniqueId, SessionMessageReplay(payload, sessionInfo.responderId))
        heartbeatManager.sessionMessageSent(
            initiatorHandshakeUniqueId,
            SessionKey(ourMemberInfo.holdingIdentity, responderMemberInfo.holdingIdentity),
            message.header.sessionId,
            ::destroyOutboundSession
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
        val initiatorHandshakeUniqueId = message.header.sessionId + "_" + InitiatorHandshakeMessage::class.java.simpleName
        sessionReplayer.removeMessageFromReplay(initiatorHandshakeUniqueId)
        heartbeatManager.messageAcknowledged(initiatorHandshakeUniqueId)
        sessionNegotiationLock.write {
            activeOutboundSessions[sessionInfo] = message.header.sessionId to authenticatedSession
            activeOutboundSessionsById[message.header.sessionId] = authenticatedSession
            pendingOutboundSessions.remove(message.header.sessionId)
            pendingOutboundSessionKeys.remove(sessionInfo)
            pendingOutboundSessionMessageQueues.sessionNegotiatedCallback(this, sessionInfo, authenticatedSession, networkMap)
        }
        return null
    }

    private fun processInitiatorHello(message: InitiatorHelloMessage): LinkOutMessage? {
        val session = pendingInboundSessions.computeIfAbsent(message.header.sessionId) { sessionId ->
            val session = protocolFactory.createResponder(
                sessionId,
                config.protocolModes,
                config.maxMessageSize
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
            logger.error("The message was discarded. ${exception.message}")
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
         * We delay removing the session from pendingInboundSessions until we receive the first data message as before this point
         * the other side (Initiator) might replay [InitiatorHandshakeMessage] in the case where the [ResponderHandshakeMessage] was lost.
         * */
        return createLinkOutMessage(response, peer, networkType)
    }

    private class HeartbeatManager(
        publisherFactory: PublisherFactory,
        private val networkMap: LinkManagerNetworkMap,
        private val heartbeatPeriod: Duration,
        private val sessionTimeout: Duration,
    ) : Lifecycle {

        companion object {
            private val logger = contextLogger()
            const val HEARTBEAT_MANAGER_CLIENT_ID = "heartbeat-manager-client"
        }

        @Volatile
        private var running = false
        private val startStopLock = ReentrantReadWriteLock()
        private lateinit var executorService: ScheduledExecutorService

        private val sessionKeys = ConcurrentHashMap<String, SessionKey>()
        private val trackedSessions = ConcurrentHashMap<SessionKey, TrackedSession>()

        private val config = PublisherConfig(HEARTBEAT_MANAGER_CLIENT_ID, 1)
        private val publisher = publisherFactory.createPublisher(config)

        /**
         * For each Session we track the following.
         * [lastSendTimestamp]: The last time we sent a message using this Session.
         * [lastAckTimestamp]: The last time we acknowledged a message sent using this Session.
         * [sentMessageIds]: The messageId's of each sent message.
         * [nextSequenceNumber]: The next sequence number to add to the Heartbeat Message for debug purposes.
         */
        class TrackedSession(
            var lastSendTimestamp: Long,
            var lastAckTimestamp: Long,
            val sentMessageIds: MutableSet<String> = mutableSetOf(),
            var nextSequenceNumber: Long = 1L,
            var sendingHeartbeats: Boolean = false
        )

        override val isRunning: Boolean
            get() = running

        override fun start() {
            startStopLock.write {
                if (!running) {
                    executorService = Executors.newSingleThreadScheduledExecutor()
                    publisher.start()
                    running = true
                }
            }
        }

        override fun stop() {
            startStopLock.write {
                if (running) {
                    executorService.shutdownNow()
                    publisher.close()
                    running = false
                }
            }
        }

        fun sessionMessageSent(
            messageId: String,
            key: SessionKey,
            sessionId: String,
            destroySession: (key: SessionKey, sessionId: String) -> Any
        ) {
            startStopLock.read {
                if (!running) {
                    throw IllegalStateException("A session message was added before the HeartbeatManager was started.")
                }
                trackedSessions.compute(key) { _, initialTrackedSession ->
                    return@compute if (initialTrackedSession != null) {
                        initialTrackedSession.lastSendTimestamp = timeStamp()
                        initialTrackedSession.sentMessageIds.add(messageId)
                        initialTrackedSession
                    } else {
                        executorService.schedule(
                            { sessionTimeout(key, sessionId, destroySession) },
                            sessionTimeout.toMillis(),
                            TimeUnit.MILLISECONDS
                        )
                        val trackedSession = TrackedSession(timeStamp(), timeStamp())
                        trackedSession.sentMessageIds.add(messageId)
                        trackedSession
                    }
                }
            }
        }

        fun messageSent(messageId: String, key: SessionKey, session: Session) {
            startStopLock.read {
                if (!running) {
                    throw IllegalStateException("A message was sent before the HeartbeatManager was started.")
                }
                val trackedSession = trackedSessions.computeIfPresent(key) { _, trackedSession ->
                    trackedSession.lastSendTimestamp = timeStamp()
                    trackedSession.sentMessageIds.add(messageId)
                    if (!trackedSession.sendingHeartbeats) {
                        executorService.schedule({ sendHeartbeat(key, session) }, heartbeatPeriod.toMillis(), TimeUnit.MILLISECONDS)
                        trackedSession.sendingHeartbeats = true
                    }
                    trackedSession
                }
                if (trackedSession != null) {
                    sessionKeys[messageId] = key
                } else {
                    throw IllegalStateException("A message with ID $messageId, was sent on a session between ${key.ourId} and " +
                            "${key.responderId}}, which is not tracked.")
                }
            }
        }

        fun messageAcknowledged(messageId: String) {
            startStopLock.read {
                if (!running) {
                    throw IllegalStateException("A message was acknowledged before the HeartbeatManager was started.")
                }
                val sessionKey = sessionKeys[messageId] ?: return
                val sessionInfo = trackedSessions[sessionKey] ?: return
                logger.trace("Message acknowledged with Id $messageId.")
                sessionInfo.lastAckTimestamp = timeStamp()
                sessionInfo.sentMessageIds.remove(messageId)
            }
        }

        private fun sessionTimeout(key: SessionKey, sessionId: String, destroySession: (key: SessionKey, sessionId: String) -> Any) {
            val sessionInfo = trackedSessions[key] ?: return
            val timeSinceLastAck = timeStamp() - sessionInfo.lastAckTimestamp
            if (timeSinceLastAck >= sessionTimeout.toMillis()) {
                destroySession(key, sessionId)
                for (messageId in sessionInfo.sentMessageIds) {
                    sessionKeys.remove(messageId)
                }
                trackedSessions.remove(key)
            } else {
                executorService.schedule(
                    {sessionTimeout(key, sessionId, destroySession)},
                    sessionTimeout.toMillis() - timeSinceLastAck,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun sendHeartbeat(sessionKey: SessionKey, session: Session) {
            val sessionInfo = trackedSessions[sessionKey] ?: return
            val timeSinceLastAck = timeStamp() - sessionInfo.lastAckTimestamp
            val timeSinceLastSend = timeStamp() - sessionInfo.lastSendTimestamp

            if (timeSinceLastAck >= sessionTimeout.toMillis()) return
            if (timeSinceLastSend >= heartbeatPeriod.toMillis()) {
                logger.trace("Sending heartbeat message between ${sessionKey.ourId} (our Identity) and ${sessionKey.responderId}.")
                val heartBeatMessageId = LinkManager.generateKey()
                sessionInfo.sentMessageIds.add(heartBeatMessageId)
                sessionKeys[heartBeatMessageId] = sessionKey
                @Suppress("TooGenericExceptionCaught")
                try {
                    sendHeartbeatMessage(
                        heartBeatMessageId,
                        sessionKey.ourId.toHoldingIdentity(),
                        sessionKey.responderId.toHoldingIdentity(),
                        session,
                        sessionInfo.nextSequenceNumber++
                    )
                } catch (exception: Exception) {
                    logger.error("An exception was thrown when sending a heartbeat message. The task will be retried again in" +
                            " ${sessionTimeout.toMillis()} ms.\nException:", exception)
                }
                executorService.schedule({ sendHeartbeat(sessionKey, session) }, heartbeatPeriod.toMillis(), TimeUnit.MILLISECONDS)
            } else {
                executorService.schedule(
                    { sendHeartbeat(sessionKey, session) },
                    heartbeatPeriod.toMillis() - timeSinceLastSend,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun sendHeartbeatMessage(
            messageId: String,
            source: HoldingIdentity,
            dest: HoldingIdentity,
            session: Session,
            sequenceNumber: Long
        ) {
            val heartbeatMessage = HeartbeatMessage(dest, source, messageId, sequenceNumber)
            val future = publisher.publish(
                listOf(
                    Record(
                        Schema.LINK_OUT_TOPIC,
                        messageId,
                        MessageConverter.linkOutMessageFromHeartbeat(source, dest, heartbeatMessage, session, networkMap)
                    )
                )
            )
            future.single().getOrThrow()
        }

        private fun timeStamp(): Long {
            return Instant.now().toEpochMilli()
        }
    }
}