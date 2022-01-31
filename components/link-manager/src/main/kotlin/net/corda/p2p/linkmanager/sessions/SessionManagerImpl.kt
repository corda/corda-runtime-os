package net.corda.p2p.linkmanager.sessions

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.HeartbeatMessage
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
import net.corda.p2p.linkmanager.MessageHeaderFactory
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromHeartbeat
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionKey
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourIdNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerHashNotInNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerNotInTheNetworkMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.validationFailedWarning
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Suppress("LongParameterList", "TooManyFunctions")
open class SessionManagerImpl(
    private val networkMap: LinkManagerNetworkMap,
    private val cryptoService: LinkManagerCryptoService,
    private val pendingOutboundSessionMessageQueues: LinkManager.PendingSessionMessageQueues,
    publisherFactory: PublisherFactory,
    private val configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    configuration: SmartConfig,
    private val messageHeaderFactory: MessageHeaderFactory,
    private val protocolFactory: ProtocolFactory = CryptoProtocolFactory(),
    private val sessionReplayer: InMemorySessionReplayer = InMemorySessionReplayer(
        publisherFactory,
        configurationReaderService,
        coordinatorFactory,
        configuration,
        messageHeaderFactory,
    ),
) : SessionManager {

    companion object {
        fun getSessionKeyFromMessage(message: AuthenticatedMessage): SessionKey {
            val peer = message.header.destination.toHoldingIdentity()
            val us = message.header.source.toHoldingIdentity()
            return SessionKey(us, peer)
        }
        private const val SESSION_MANAGER_CLIENT_ID = "session-manager"
    }

    private val pendingOutboundSessions = ConcurrentHashMap<String, Pair<SessionKey, AuthenticationProtocolInitiator>>()
    private val pendingOutboundSessionKeys = ConcurrentHashMap.newKeySet<SessionKey>()
    private val activeOutboundSessions = ConcurrentHashMap<SessionKey, Session>()
    private val activeOutboundSessionsById = ConcurrentHashMap<String, Pair<SessionKey, Session>>()

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Pair<SessionKey, Session>>()

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    private val sessionNegotiationLock = ReentrantReadWriteLock()

    private val config = AtomicReference<SessionManagerConfig>()

    private val heartbeatManager: HeartbeatManager = HeartbeatManager(
        publisherFactory,
        configurationReaderService,
        coordinatorFactory,
        configuration,
        messageHeaderFactory,
        ::destroyOutboundSession,
    )

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(SESSION_MANAGER_CLIENT_ID),
        configuration
    )

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        children = setOf(
            heartbeatManager.dominoTile, sessionReplayer.dominoTile, networkMap.dominoTile, cryptoService.dominoTile,
            pendingOutboundSessionMessageQueues.dominoTile, publisher.dominoTile
        ),
        configurationChangeHandler = SessionManagerConfigChangeHandler()
    )

    @VisibleForTesting
    internal data class SessionManagerConfig(
        val maxMessageSize: Int,
        val protocolModes: Set<ProtocolMode>,
    )

    internal inner class SessionManagerConfigChangeHandler: ConfigurationChangeHandler<SessionManagerConfig>(
        configurationReaderService,
        LinkManagerConfiguration.CONFIG_KEY,
        ::fromConfig
    ) {
        override fun applyNewConfiguration(
            newConfiguration: SessionManagerConfig,
            oldConfiguration: SessionManagerConfig?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val configUpdateResult = CompletableFuture<Unit>()
            dominoTile.withLifecycleWriteLock {
                config.set(newConfiguration)
                destroyAllSessions()
            }
            configUpdateResult.complete(Unit)
            return configUpdateResult
        }
    }

    private fun fromConfig(config: Config): SessionManagerConfig {
        return SessionManagerConfig(config.getInt(LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY),
        config.getEnumList(ProtocolMode::class.java, LinkManagerConfiguration.PROTOCOL_MODE_KEY).toSet())
    }

    override fun processOutboundMessage(message: AuthenticatedMessageAndKey): SessionState {
        return dominoTile.withLifecycleLock {
            sessionNegotiationLock.read {
                val key = getSessionKeyFromMessage(message.message)

                val activeSession = activeOutboundSessions[key]
                if (activeSession != null) {
                    return@read SessionState.SessionEstablished(activeSession)
                }
                pendingOutboundSessionMessageQueues.queueMessage(message, key)
                if (pendingOutboundSessionKeys.contains(key)) {
                    return@read SessionState.SessionAlreadyPending
                } else {
                    val (sessionId, initMessage) = getSessionInitMessage(key) ?: return@read SessionState.CannotEstablishSession
                    return@read SessionState.NewSessionNeeded(sessionId, initMessage)
                }
            }
        }
    }

    override fun getSessionById(uuid: String): SessionManager.SessionDirection {
        return dominoTile.withLifecycleLock {
            val inboundSession = activeInboundSessions[uuid]
            if (inboundSession != null) {
                return@withLifecycleLock SessionManager.SessionDirection.Inbound(inboundSession.first, inboundSession.second)
            }
            val outboundSession = activeOutboundSessionsById[uuid]
            return@withLifecycleLock if (outboundSession != null) {
                SessionManager.SessionDirection.Outbound(outboundSession.first, outboundSession.second)
            } else {
                SessionManager.SessionDirection.NoSession
            }
        }
    }

    override fun processSessionMessage(message: LinkInMessage): LinkOutMessage? {
        return dominoTile.withLifecycleLock {
            when (val payload = message.payload) {
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

    override fun dataMessageSent(session: Session) {
        dominoTile.withLifecycleLock {
            heartbeatManager.dataMessageSent(session)
        }
    }

    override fun messageAcknowledged(sessionId: String) {
        dominoTile.withLifecycleLock {
            heartbeatManager.messageAcknowledged(sessionId)
        }
    }

    private fun destroyAllSessions() {
        sessionReplayer.removeAllMessagesFromReplay()
        heartbeatManager.stopTrackingAllSessions()
        val tombstoneRecords = (activeOutboundSessionsById.keys + pendingOutboundSessions.keys + activeInboundSessions.keys
                + pendingInboundSessions.keys).map { Record(SESSION_OUT_PARTITIONS, it, null) }
        activeOutboundSessions.clear()
        activeOutboundSessionsById.clear()
        pendingOutboundSessions.clear()
        pendingOutboundSessionKeys.clear()

        activeInboundSessions.clear()
        pendingInboundSessions.clear()
        //This is suboptimal we could instead restart session negotiation
        pendingOutboundSessionMessageQueues.destroyAllQueues()
        if (tombstoneRecords.isNotEmpty()) {
            publisher.publish(tombstoneRecords)
        }
    }

    private fun destroyOutboundSession(sessionKey: SessionKey, sessionId: String) {
        sessionNegotiationLock.write {
            sessionReplayer.removeMessageFromReplay(initiatorHandshakeUniqueId(sessionId))
            sessionReplayer.removeMessageFromReplay(initiatorHelloUniqueId(sessionId))
            activeOutboundSessions.remove(sessionKey)
            activeOutboundSessionsById.remove(sessionId)
            pendingOutboundSessions.remove(sessionId)
            pendingOutboundSessionKeys.remove(sessionKey)
            pendingOutboundSessionMessageQueues.destroyQueue(sessionKey)
            publisher.publish(listOf(Record(SESSION_OUT_PARTITIONS, sessionId, null)))
        }
    }

    private fun initiatorHelloUniqueId(sessionId: String): String {
        return sessionId + "_" + InitiatorHelloMessage::class.java.simpleName
    }

    private fun initiatorHandshakeUniqueId(sessionId: String): String {
        return sessionId + "_" + InitiatorHandshakeMessage::class.java.simpleName
    }

    private fun getSessionInitMessage(sessionKey: SessionKey): Pair<String, LinkOutMessage>? {
        val sessionId = UUID.randomUUID().toString()

        val ourMemberInfo = networkMap.getMemberInfo(sessionKey.ourId)
        if (ourMemberInfo == null) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionKey.responderId} but our identity ${sessionKey.ourId}" +
                        " is not in the network map. The sessionInit message was not sent."
            )
            return null
        }

        val sessionManagerConfig = config.get()
        val session = protocolFactory.createInitiator(
            sessionId,
            sessionManagerConfig.protocolModes,
            sessionManagerConfig.maxMessageSize,
            ourMemberInfo.publicKey,
            ourMemberInfo.holdingIdentity.groupId
        )

        pendingOutboundSessionKeys.add(sessionKey)
        pendingOutboundSessions[sessionId] = Pair(sessionKey, session)
        logger.info("Local identity (${sessionKey.ourId}) initiating new session $sessionId with remote identity ${sessionKey.responderId}")

        val sessionInitPayload = session.generateInitiatorHello()
        sessionReplayer.addMessageForReplay(
            initiatorHelloUniqueId(sessionId),
            InMemorySessionReplayer.SessionMessageReplay(
                sessionInitPayload,
                sessionId,
                sessionKey.ourId,
                sessionKey.responderId,
                heartbeatManager::sessionMessageSent
            )
        )

        val responderMemberInfo = networkMap.getMemberInfo(sessionKey.responderId)
        if (responderMemberInfo == null) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionKey.responderId} which is not in the network map. " +
                        "The sessionInit message was not sent."
            )
            return null
        }
        heartbeatManager.sessionMessageSent(sessionKey, sessionId)

        val header = messageHeaderFactory.createLinkOutHeader(sessionKey.ourId, responderMemberInfo.holdingIdentity) ?: return null

        return createLinkOutMessage(sessionInitPayload, header).let {
            sessionId to it
        }
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
            logger.warn(
                "${exception.message}. The ${message::class.java.simpleName} with sessionId ${message.header.sessionId}" +
                        " was discarded."
            )
            return null
        }

        sessionReplayer.removeMessageFromReplay(initiatorHelloUniqueId(message.header.sessionId))
        heartbeatManager.messageAcknowledged(message.header.sessionId)

        sessionReplayer.addMessageForReplay(
            initiatorHandshakeUniqueId(message.header.sessionId),
            InMemorySessionReplayer.SessionMessageReplay(
                payload,
                message.header.sessionId,
                sessionInfo.ourId,
                sessionInfo.responderId,
                heartbeatManager::sessionMessageSent
            )
        )

        val header = messageHeaderFactory.createLinkOutHeader(
            ourMemberInfo.holdingIdentity,
            responderMemberInfo.holdingIdentity
        ) ?: return null

        heartbeatManager.sessionMessageSent(
            SessionKey(ourMemberInfo.holdingIdentity, responderMemberInfo.holdingIdentity),
            message.header.sessionId,
        )

        return createLinkOutMessage(payload, header)
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
        sessionReplayer.removeMessageFromReplay(initiatorHandshakeUniqueId(message.header.sessionId))
        heartbeatManager.messageAcknowledged(message.header.sessionId)
        sessionNegotiationLock.write {
            activeOutboundSessions[sessionInfo] = authenticatedSession
            activeOutboundSessionsById[message.header.sessionId] = Pair(sessionInfo, authenticatedSession)
            pendingOutboundSessions.remove(message.header.sessionId)
            pendingOutboundSessionKeys.remove(sessionInfo)
            pendingOutboundSessionMessageQueues.sessionNegotiatedCallback(
                this,
                sessionInfo,
                authenticatedSession,
                networkMap,
                messageHeaderFactory
            )
        }
        logger.info("Outbound session ${authenticatedSession.sessionId} established " +
                "(local=${sessionInfo.ourId}, remote=${sessionInfo.responderId}).")
        return null
    }

    private fun processInitiatorHello(message: InitiatorHelloMessage): LinkOutMessage? {
        val sessionManagerConfig = config.get()
        val session = pendingInboundSessions.computeIfAbsent(message.header.sessionId) { sessionId ->
            val session = protocolFactory.createResponder(
                sessionId,
                sessionManagerConfig.protocolModes,
                sessionManagerConfig.maxMessageSize
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

        val header = messageHeaderFactory.createLinkOutHeader(peer.holdingIdentity) ?: return null

        logger.info("Remote identity ${peer.holdingIdentity} initiated new session ${message.header.sessionId}.")
        return createLinkOutMessage(responderHello, header)
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

        val header = messageHeaderFactory.createLinkOutHeader(ourMemberInfo.holdingIdentity, peer.holdingIdentity) ?: return null


        val response = try {
            val ourPublicKey = ourMemberInfo.publicKey
            val signData = { data: ByteArray ->
                cryptoService.signData(ourMemberInfo.publicKey, data)
            }
            session.generateOurHandshakeMessage(ourPublicKey, signData)
        } catch (exception: LinkManagerCryptoService.NoPrivateKeyForGroupException) {
            logger.warn(
                "Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                        " The message was discarded."
            )
            return null
        }

        activeInboundSessions[message.header.sessionId] = Pair(
            SessionKey(ourMemberInfo.holdingIdentity, peer.holdingIdentity),
            session.getSession()
        )
        logger.info("Inbound session ${message.header.sessionId} established " +
                "(local=${ourMemberInfo.holdingIdentity}, remote=${peer.holdingIdentity}).")
        /**
         * We delay removing the session from pendingInboundSessions until we receive the first data message as before this point
         * the other side (Initiator) might replay [InitiatorHandshakeMessage] in the case where the [ResponderHandshakeMessage] was lost.
         * */
        return createLinkOutMessage(response, header)
    }

    class HeartbeatManager(
        publisherFactory: PublisherFactory,
        private val configurationReaderService: ConfigurationReadService,
        coordinatorFactory: LifecycleCoordinatorFactory,
        configuration: SmartConfig,
        private var messageHeaderFactory: MessageHeaderFactory,
        private val destroySession: (key: SessionKey, sessionId: String) -> Any,
    ) : LifecycleWithDominoTile {

        companion object {
            private val logger = contextLogger()
            const val HEARTBEAT_MANAGER_CLIENT_ID = "heartbeat-manager-client"
        }

        private val config = AtomicReference<HeartbeatManagerConfig>()

        @VisibleForTesting
        internal data class HeartbeatManagerConfig(
            val heartbeatPeriod: Duration,
            val sessionTimeout: Duration
        )

        @VisibleForTesting
        internal inner class HeartbeatManagerConfigChangeHandler: ConfigurationChangeHandler<HeartbeatManagerConfig>(
            configurationReaderService,
            LinkManagerConfiguration.CONFIG_KEY,
            ::fromConfig
        ) {
            override fun applyNewConfiguration(
                newConfiguration: HeartbeatManagerConfig,
                oldConfiguration: HeartbeatManagerConfig?,
                resources: ResourcesHolder,
            ): CompletableFuture<Unit> {
                val configUpdateResult = CompletableFuture<Unit>()
                config.set(newConfiguration)
                configUpdateResult.complete(Unit)
                return configUpdateResult
            }
        }

        private fun fromConfig(config: Config): HeartbeatManagerConfig {
            return HeartbeatManagerConfig(Duration.ofMillis(config.getLong(LinkManagerConfiguration.HEARTBEAT_MESSAGE_PERIOD_KEY)),
            Duration.ofMillis(config.getLong(LinkManagerConfiguration.SESSION_TIMEOUT_KEY)))
        }

        private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
            val future = CompletableFuture<Unit>()
            executorService = Executors.newSingleThreadScheduledExecutor()
            resources.keep(AutoClosableScheduledExecutorService(executorService))
            future.complete(Unit)
            return future
        }

        @Volatile
        private lateinit var executorService: ScheduledExecutorService

        private val trackedSessions = ConcurrentHashMap<String, TrackedSession>()

        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
            PublisherConfig(HEARTBEAT_MANAGER_CLIENT_ID),
            configuration
        )

        override val dominoTile = DominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            ::createResources,
            setOf(publisher.dominoTile),
            HeartbeatManagerConfigChangeHandler(),
        )

        /**
         * For each Session we track the following.
         * [identityData]: The source and destination identities for this Session.
         * [lastSendTimestamp]: The last time we sent a message using this Session.
         * [lastAckTimestamp]: The last time a message we sent via this Session was acknowledged by the other side.
         * [sendingHeartbeats]: If true we send heartbeats to the counterparty (this happens after the session established).
         */
        class TrackedSession(
            val identityData: SessionKey,
            @Volatile
            var lastSendTimestamp: Long,
            @Volatile
            var lastAckTimestamp: Long,
            @Volatile
            var sendingHeartbeats: Boolean = false
        )

        fun stopTrackingAllSessions() {
            trackedSessions.clear()
        }

        fun sessionMessageSent(key: SessionKey, sessionId: String) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A session message was added before the HeartbeatManager was started.")
                }
                trackedSessions.compute(sessionId) { _, initialTrackedSession ->
                    if (initialTrackedSession != null) {
                        initialTrackedSession.lastSendTimestamp = timeStamp()
                        initialTrackedSession
                    } else {
                        executorService.schedule(
                            { sessionTimeout(key, sessionId) },
                            config.get().sessionTimeout.toMillis(),
                            TimeUnit.MILLISECONDS
                        )
                        TrackedSession(key, timeStamp(), timeStamp())
                    }
                }
            }
        }

        fun dataMessageSent(session: Session) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was sent before the HeartbeatManager was started.")
                }
                trackedSessions.computeIfPresent(session.sessionId) { _, trackedSession ->
                    trackedSession.lastSendTimestamp = timeStamp()
                    if (!trackedSession.sendingHeartbeats) {
                        executorService.schedule(
                            { sendHeartbeat(trackedSession.identityData, session) },
                            config.get().heartbeatPeriod.toMillis(),
                            TimeUnit.MILLISECONDS
                        )
                        trackedSession.sendingHeartbeats = true
                    }
                    trackedSession
                } ?: throw IllegalStateException("A message was sent on session with Id ${session.sessionId} which is not tracked.")
            }
        }

        fun messageAcknowledged(sessionId: String) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was acknowledged before the HeartbeatManager was started.")
                }
                val sessionInfo = trackedSessions[sessionId] ?: return@withLifecycleLock
                logger.trace("Message acknowledged with on a session with Id $sessionId.")
                sessionInfo.lastAckTimestamp = timeStamp()
            }
        }

        private fun sessionTimeout(key: SessionKey, sessionId: String) {
            val sessionInfo = trackedSessions[sessionId] ?: return
            val timeSinceLastAck = timeStamp() - sessionInfo.lastAckTimestamp
            if (timeSinceLastAck >= config.get().sessionTimeout.toMillis()) {
                logger.info("Outbound session $sessionId (local=${key.ourId}, remote=${key.responderId}) timed out due to inactivity and " +
                        "it will be cleaned up.")
                destroySession(key, sessionId)
                trackedSessions.remove(sessionId)
            } else {
                executorService.schedule(
                    { sessionTimeout(key, sessionId) },
                    config.get().sessionTimeout.toMillis() - timeSinceLastAck,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun sendHeartbeat(sessionKey: SessionKey, session: Session) {
            val sessionInfo = trackedSessions[session.sessionId]
            if (sessionInfo == null) {
                logger.info("Stopped sending heartbeats for session (${session.sessionId}), which expired.")
                return
            }
            val config = config.get()

            val timeSinceLastSend = timeStamp() - sessionInfo.lastSendTimestamp
            if (timeSinceLastSend >= config.heartbeatPeriod.toMillis()) {
                logger.trace { "Sending heartbeat message between ${sessionKey.ourId} (our Identity) and ${sessionKey.responderId}." }
                sendHeartbeatMessage(
                    sessionKey.ourId.toHoldingIdentity(),
                    sessionKey.responderId.toHoldingIdentity(),
                    session,
                )
                executorService.schedule({ sendHeartbeat(sessionKey, session) }, config.heartbeatPeriod.toMillis(), TimeUnit.MILLISECONDS)
            } else {
                executorService.schedule(
                    { sendHeartbeat(sessionKey, session) },
                    config.heartbeatPeriod.toMillis() - timeSinceLastSend,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun sendHeartbeatMessage(source: HoldingIdentity, dest: HoldingIdentity, session: Session) {
            val heartbeatMessage = HeartbeatMessage()
            val message = linkOutMessageFromHeartbeat(source, dest, heartbeatMessage, session, messageHeaderFactory)
            if (message == null) {
                logger.warn("Failed to send a Heartbeat between $source and $dest.")
                return
            }
            val future = publisher.publish(
                listOf(
                    Record(
                        LINK_OUT_TOPIC,
                        UUID.randomUUID().toString(),
                        message
                    )
                )
            )
            future.single().exceptionally { error ->
                logger.warn("An exception was thrown when sending a heartbeat message.\nException:", error)
            }
        }

        private fun timeStamp(): Long {
            return Instant.now().toEpochMilli()
        }
    }
}
