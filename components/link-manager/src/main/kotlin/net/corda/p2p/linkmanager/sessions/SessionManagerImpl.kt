package net.corda.p2p.linkmanager.sessions

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
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
import net.corda.p2p.SessionPartitions
import net.corda.p2p.app.AuthenticatedMessage
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
import net.corda.p2p.linkmanager.InboundAssignmentListener
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.LinkManagerHostingMap
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toHoldingIdentity
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toLMNetworkType
import net.corda.p2p.linkmanager.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.getSignatureSpec
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionCounterparties
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.couldNotFindGroupInfo
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.ourIdNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.peerNotInTheMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.Companion.validationFailedWarning
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessorException
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.*
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
    private val groups: LinkManagerGroupPolicyProvider,
    private val members: LinkManagerMembershipGroupReader,
    private val cryptoProcessor: CryptoProcessor,
    private val pendingOutboundSessionMessageQueues: LinkManager.PendingSessionMessageQueues,
    publisherFactory: PublisherFactory,
    private val configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    configuration: SmartConfig,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val protocolFactory: ProtocolFactory = CryptoProtocolFactory(),
    clock: Clock,
    private val sessionReplayer: InMemorySessionReplayer = InMemorySessionReplayer(
        publisherFactory,
        configurationReaderService,
        coordinatorFactory,
        configuration,
        groups,
        members,
        clock,
    ),

    executorServiceFactory: () -> ScheduledExecutorService = {Executors.newSingleThreadScheduledExecutor()},
) : SessionManager {

    companion object {
        fun getSessionCounterpartiesFromMessage(message: AuthenticatedMessage): SessionCounterparties {
            val peer = message.header.destination.toHoldingIdentity()
            val us = message.header.source.toHoldingIdentity()
            return SessionCounterparties(us, peer)
        }
        private const val SESSION_MANAGER_CLIENT_ID = "session-manager"
    }

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Pair<SessionCounterparties, Session>>()

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    private val sessionNegotiationLock = ReentrantReadWriteLock()

    private val config = AtomicReference<SessionManagerConfig>()

    private val heartbeatManager: HeartbeatManager = HeartbeatManager(
        publisherFactory,
        configurationReaderService,
        coordinatorFactory,
        configuration,
        groups,
        members,
        ::refreshOutboundSession,
        clock,
        executorServiceFactory
    )
    private val outboundSessionPool = OutboundSessionPool(heartbeatManager::calculateWeightForSession)

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(SESSION_MANAGER_CLIENT_ID),
        configuration
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        ::createResources,
        dependentChildren = setOf(
            heartbeatManager.dominoTile, sessionReplayer.dominoTile, groups.dominoTile, members.dominoTile, cryptoProcessor.dominoTile,
            pendingOutboundSessionMessageQueues.dominoTile, publisher.dominoTile, linkManagerHostingMap.dominoTile
        ),
        managedChildren = setOf(heartbeatManager.dominoTile, sessionReplayer.dominoTile, publisher.dominoTile),
        configurationChangeHandler = SessionManagerConfigChangeHandler()
    )

    @VisibleForTesting
    internal data class SessionManagerConfig(
        val maxMessageSize: Int,
        val sessionsPerCounterparties: Int,
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
                if (oldConfiguration != null) {
                    logger.info("The Session Manager got new config. All sessions will be cleaned up.")
                    sessionReplayer.removeAllMessagesFromReplay()
                    heartbeatManager.stopTrackingAllSessions()
                    val tombstoneRecords = (outboundSessionPool.getAllSessionIds() + activeInboundSessions.keys
                            + pendingInboundSessions.keys).map { Record(SESSION_OUT_PARTITIONS, it, null) }
                    outboundSessionPool.clearPool()
                    activeInboundSessions.clear()
                    pendingInboundSessions.clear()
                    //This is suboptimal we could instead restart session negotiation
                    pendingOutboundSessionMessageQueues.destroyAllQueues()
                    if (tombstoneRecords.isNotEmpty()) {
                        publisher.publish(tombstoneRecords)
                    }
                }
            }
            configUpdateResult.complete(Unit)
            return configUpdateResult
        }
    }

    private fun fromConfig(config: Config): SessionManagerConfig {
        return SessionManagerConfig(
            config.getInt(LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY),
            config.getInt(LinkManagerConfiguration.SESSIONS_PER_PEER_KEY)
        )
    }

    override fun processOutboundMessage(message: AuthenticatedMessageAndKey): SessionState {
        return dominoTile.withLifecycleLock {
            sessionNegotiationLock.read {
                val counterparties = getSessionCounterpartiesFromMessage(message.message)

                return@read when (val status = outboundSessionPool.getNextSession(counterparties)) {
                    is OutboundSessionPool.SessionPoolStatus.SessionActive -> SessionState.SessionEstablished(status.session)
                    is OutboundSessionPool.SessionPoolStatus.SessionPending -> {
                        SessionState.SessionAlreadyPending
                    }
                    is OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded -> {
                        val initMessages = genSessionInitMessages(counterparties, config.get().sessionsPerCounterparties)
                        if (initMessages.isEmpty()) return@read SessionState.CannotEstablishSession
                        outboundSessionPool.addPendingSessions(counterparties, initMessages.map { it.first })
                        val messages = linkOutMessagesFromSessionInitMessages(counterparties, initMessages)
                            ?: return@read SessionState.CannotEstablishSession
                        SessionState.NewSessionsNeeded(messages)
                    }
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
            val outboundSession = outboundSessionPool.getSession(uuid)
            return@withLifecycleLock if (outboundSession is OutboundSessionPool.SessionType.ActiveSession) {
                SessionManager.SessionDirection.Outbound(outboundSession.sessionCounterparties, outboundSession.session)
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

    private fun createResources(@Suppress("UNUSED_PARAMETER") resourcesHolder: ResourcesHolder): CompletableFuture<Unit> {
        inboundAssignmentListener.registerCallbackForTopic(Schemas.P2P.LINK_IN_TOPIC) { partitions ->
            val sessionIds = outboundSessionPool.getAllSessionIds() + pendingInboundSessions.keys + activeInboundSessions.keys
            val records = sessionIds.map { sessionId ->
                Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(partitions.toList()))
            }
            if (records.isNotEmpty()) publisher.publish(records)
        }
        return CompletableFuture.completedFuture(Unit)
    }

    private fun refreshOutboundSession(counterparties: SessionCounterparties, sessionId: String) {
        sessionNegotiationLock.write {
            sessionReplayer.removeMessageFromReplay(initiatorHandshakeUniqueId(sessionId), counterparties)
            sessionReplayer.removeMessageFromReplay(initiatorHelloUniqueId(sessionId), counterparties)
            val sessionInitMessage = genSessionInitMessages(counterparties, 1)
            if (!outboundSessionPool.replaceSession(sessionId, sessionInitMessage.single().first)) {
                //If the session was not replaced do not send a initiatorHello
                return
            }
            val records = linkOutMessagesFromSessionInitMessages(counterparties, sessionInitMessage) ?.let {
                LinkManager.OutboundMessageProcessor.recordsForNewSessions(
                    SessionState.NewSessionsNeeded(it),
                    inboundAssignmentListener,
                    logger
                ) + listOf(Record(SESSION_OUT_PARTITIONS, sessionId, null))
            }
            records?.let {publisher.publish(records)}
        }
    }

    private fun initiatorHelloUniqueId(sessionId: String): String {
        return sessionId + "_" + InitiatorHelloMessage::class.java.simpleName
    }

    private fun initiatorHandshakeUniqueId(sessionId: String): String {
        return sessionId + "_" + InitiatorHandshakeMessage::class.java.simpleName
    }

    private fun genSessionInitMessages(counterparties: SessionCounterparties, multiplicity: Int)
        : List<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>> {

        val groupInfo = groups.getGroupInfo(counterparties.ourId.groupId)
        if (groupInfo == null) {
            logger.warn(
                "Could not find the group information in the GroupPolicyProvider for groupId ${counterparties.ourId.groupId}." +
                        " The sessionInit message was not sent."
            )
            return emptyList()
        }

        val ourIdentityInfo = linkManagerHostingMap.getInfo(counterparties.ourId)
        if (ourIdentityInfo == null) {
            logger.warn(
                "Attempted to start session negotiation with peer ${counterparties.counterpartyId} but our identity " +
                        "${counterparties.ourId} is not in the members map. The sessionInit message was not sent."
            )
            return emptyList()
        }

        val sessionManagerConfig = config.get()
        val messagesAndProtocol = mutableListOf<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>>()
        (1..multiplicity).map {
            val sessionId = UUID.randomUUID().toString()
            val session = protocolFactory.createInitiator(
                sessionId,
                groupInfo.protocolModes,
                sessionManagerConfig.maxMessageSize,
                ourIdentityInfo.sessionPublicKey,
                ourIdentityInfo.holdingIdentity.groupId
            )
            messagesAndProtocol.add(Pair(session, session.generateInitiatorHello()))
        }
        return messagesAndProtocol
    }

    private fun linkOutMessagesFromSessionInitMessages(
        counterparties: SessionCounterparties,
        messages: List<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>>
    ): List<Pair<String, LinkOutMessage>>? {
        val sessionIds = messages.map { it.first.sessionId }
        logger.info("Local identity (${counterparties.ourId}) initiating new sessions with Ids $sessionIds with remote identity " +
            "${counterparties.counterpartyId}")

        for (message in messages) {
            sessionReplayer.addMessageForReplay(
                initiatorHelloUniqueId(message.first.sessionId),
                InMemorySessionReplayer.SessionMessageReplay(
                    message.second,
                    message.first.sessionId,
                    counterparties.ourId,
                    counterparties.counterpartyId,
                    heartbeatManager::sessionMessageSent
                ),
                counterparties
            )
        }

        val responderMemberInfo = members.getMemberInfo(counterparties.counterpartyId)
        if (responderMemberInfo == null) {
            logger.warn(
                "Attempted to start session negotiation with peer ${counterparties.counterpartyId} which is not in the members map. " +
                        "The sessionInit message was not sent."
            )
            return null
        }

        val groupInfo = groups.getGroupInfo(counterparties.ourId.groupId)
        if (groupInfo == null) {
            logger.warn(
                "Could not find the group information in the GroupPolicyProvider for groupId ${counterparties.ourId.groupId}." +
                        " The sessionInit message was not sent."
            )
            return emptyList()
        }

        val linkOutMessages = mutableListOf<Pair<String, LinkOutMessage>>()
        for (message in messages) {
            heartbeatManager.sessionMessageSent(counterparties, message.first.sessionId)
            linkOutMessages.add(
                Pair(
                    message.first.sessionId,
                    createLinkOutMessage(message.second, responderMemberInfo, groupInfo.networkType.toLMNetworkType())
                )
            )
        }
        return linkOutMessages
    }

    private fun ByteArray.toBase64(): String {
        return Base64.getEncoder().encodeToString(this)
    }

    @Suppress("ComplexMethod")
    private fun processResponderHello(message: ResponderHelloMessage): LinkOutMessage? {
        logger.info("Processing ${message::class.java.simpleName} for session ${message.header.sessionId}.")

        val sessionType = outboundSessionPool.getSession(message.header.sessionId) ?: run {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val (sessionInfo, session) = when (sessionType) {
            is OutboundSessionPool.SessionType.ActiveSession -> {
                logger.alreadySessionWarning(message::class.java.simpleName, message.header.sessionId)
                return null
            }
            is OutboundSessionPool.SessionType.PendingSession -> {
                Pair(sessionType.sessionCounterparties, sessionType.protocol)
            }
        }

        session.receiveResponderHello(message)
        session.generateHandshakeSecrets()

        val ourIdentityInfo = linkManagerHostingMap.getInfo(sessionInfo.ourId)
        if (ourIdentityInfo == null) {
            logger.ourIdNotInMembersMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.ourId)
            return null
        }

        val responderMemberInfo = members.getMemberInfo(sessionInfo.counterpartyId)
        if (responderMemberInfo == null) {
            logger.peerNotInTheMembersMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.counterpartyId)
            return null
        }

        val tenantId = ourIdentityInfo.sessionKeyTenantId

        val signWithOurGroupId = { data: ByteArray ->
            cryptoProcessor.sign(
                tenantId,
                ourIdentityInfo.sessionPublicKey,
                ourIdentityInfo.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                data
            )
        }
        val payload = try {
            session.generateOurHandshakeMessage(
                responderMemberInfo.sessionPublicKey,
                signWithOurGroupId
            )
        } catch (exception: CryptoProcessorException) {
            logger.warn(
                "${exception.message}. The ${message::class.java.simpleName} with sessionId ${message.header.sessionId}" +
                        " was discarded."
            )
            return null
        }

        sessionReplayer.removeMessageFromReplay(initiatorHelloUniqueId(message.header.sessionId), sessionInfo)
        heartbeatManager.messageAcknowledged(message.header.sessionId)

        sessionReplayer.addMessageForReplay(
            initiatorHandshakeUniqueId(message.header.sessionId),
            InMemorySessionReplayer.SessionMessageReplay(
                payload,
                message.header.sessionId,
                sessionInfo.ourId,
                sessionInfo.counterpartyId,
                heartbeatManager::sessionMessageSent
            ),
            sessionInfo
        )

        val groupInfo = groups.getGroupInfo(ourIdentityInfo.holdingIdentity.groupId)
        if (groupInfo == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity.groupId)
            return null
        }
        heartbeatManager.sessionMessageSent(
            SessionCounterparties(ourIdentityInfo.holdingIdentity.toHoldingIdentity(), responderMemberInfo.holdingIdentity),
            message.header.sessionId,
        )

        return createLinkOutMessage(payload, responderMemberInfo, groupInfo.networkType.toLMNetworkType())
    }

    private fun processResponderHandshake(message: ResponderHandshakeMessage): LinkOutMessage? {
        logger.info("Processing ${message::class.java.simpleName} for session ${message.header.sessionId}.")
        val sessionType = outboundSessionPool.getSession(message.header.sessionId) ?: run {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val (sessionCounterparties, session) = when (sessionType) {
            is OutboundSessionPool.SessionType.ActiveSession -> {
                logger.alreadySessionWarning(message::class.java.simpleName, message.header.sessionId)
                return null
            }
            is OutboundSessionPool.SessionType.PendingSession -> {
                Pair(sessionType.sessionCounterparties, sessionType.protocol)
            }
        }

        val memberInfo = members.getMemberInfo(sessionCounterparties.counterpartyId)
        if (memberInfo == null) {
            logger.peerNotInTheMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                sessionCounterparties.counterpartyId
            )
            return null
        }

        try {
            session.validatePeerHandshakeMessage(message, memberInfo.sessionPublicKey, memberInfo.publicKeyAlgorithm.getSignatureSpec())
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        }
        val authenticatedSession = session.getSession()
        sessionReplayer.removeMessageFromReplay(initiatorHandshakeUniqueId(message.header.sessionId), sessionCounterparties)
        heartbeatManager.messageAcknowledged(message.header.sessionId)
        heartbeatManager.startSendingHeartbeats(authenticatedSession)
        sessionNegotiationLock.write {
            outboundSessionPool.updateAfterSessionEstablished(authenticatedSession)
            pendingOutboundSessionMessageQueues.sessionNegotiatedCallback(
                this,
                sessionCounterparties,
                authenticatedSession,
                groups,
                members
            )
        }
        logger.info("Outbound session ${authenticatedSession.sessionId} established " +
                "(local=${sessionCounterparties.ourId}, remote=${sessionCounterparties.counterpartyId}).")
        return null
    }

    private fun processInitiatorHello(message: InitiatorHelloMessage): LinkOutMessage? {
        logger.info("Processing ${message::class.java.simpleName} for session ${message.header.sessionId}.")
        val sessionManagerConfig = config.get()
        val peer = members.getMemberInfo(message.source.initiatorPublicKeyHash.array(), message.source.groupId)
        if (peer == null) {
            logger.peerHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                message.source.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }
        val groupInfo = groups.getGroupInfo(peer.holdingIdentity.groupId)
        if (groupInfo == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, peer.holdingIdentity.groupId)
            return null
        }

        val session = pendingInboundSessions.computeIfAbsent(message.header.sessionId) { sessionId ->
            val session = protocolFactory.createResponder(
                sessionId,
                groupInfo.protocolModes,
                sessionManagerConfig.maxMessageSize
            )
            session.receiveInitiatorHello(message)
            session
        }
        val responderHello = session.generateResponderHello()

        logger.info("Remote identity ${peer.holdingIdentity} initiated new session ${message.header.sessionId}.")
        return createLinkOutMessage(responderHello, peer, groupInfo.networkType.toLMNetworkType())
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage): LinkOutMessage? {
        val session = pendingInboundSessions[message.header.sessionId]
        if (session == null) {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }
        logger.info("Processing ${message::class.java.simpleName} for session ${message.header.sessionId}.")

        val initiatorIdentityData = session.getInitiatorIdentity()
        val peer = members.getMemberInfo(initiatorIdentityData.initiatorPublicKeyHash.array(), initiatorIdentityData.groupId)
        if (peer == null) {
            logger.peerHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                initiatorIdentityData.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }

        session.generateHandshakeSecrets()
        val ourIdentityData = try {
            session.validatePeerHandshakeMessage(message, peer.sessionPublicKey, peer.publicKeyAlgorithm.getSignatureSpec())
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
        val ourIdentityInfo = linkManagerHostingMap.getInfo(ourIdentityData.responderPublicKeyHash, ourIdentityData.groupId)
        if (ourIdentityInfo == null) {
            logger.ourHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                ourIdentityData.responderPublicKeyHash.toBase64()
            )
            return null
        }

        val groupInfo = groups.getGroupInfo(ourIdentityInfo.holdingIdentity.groupId)
        if (groupInfo == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity.groupId)
            return null
        }

        val tenantId = ourIdentityInfo.sessionKeyTenantId

        val response = try {
            val ourPublicKey = ourIdentityInfo.sessionPublicKey
            val signData = { data: ByteArray ->
                cryptoProcessor.sign(
                    tenantId,
                    ourIdentityInfo.sessionPublicKey,
                    ourIdentityInfo.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                    data
                )
            }
            session.generateOurHandshakeMessage(ourPublicKey, signData)
        } catch (exception: CryptoProcessorException) {
            logger.warn(
                "Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                        " The message was discarded."
            )
            return null
        }

        activeInboundSessions[message.header.sessionId] = Pair(
            SessionCounterparties(ourIdentityInfo.holdingIdentity.toHoldingIdentity(), peer.holdingIdentity),
            session.getSession()
        )
        logger.info("Inbound session ${message.header.sessionId} established " +
                "(local=${ourIdentityInfo.holdingIdentity}, remote=${peer.holdingIdentity}).")
        /**
         * We delay removing the session from pendingInboundSessions until we receive the first data message as before this point
         * the other side (Initiator) might replay [InitiatorHandshakeMessage] in the case where the [ResponderHandshakeMessage] was lost.
         * */
        return createLinkOutMessage(response, peer, groupInfo.networkType.toLMNetworkType())
    }

    class HeartbeatManager(
        publisherFactory: PublisherFactory,
        private val configurationReaderService: ConfigurationReadService,
        coordinatorFactory: LifecycleCoordinatorFactory,
        configuration: SmartConfig,
        private val groups : LinkManagerGroupPolicyProvider,
        private val members : LinkManagerMembershipGroupReader,
        private val destroySession: (counterparties: SessionCounterparties, sessionId: String) -> Any,
        private val clock: Clock,
        private val executorServiceFactory: () -> ScheduledExecutorService
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

        private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
            val future = CompletableFuture<Unit>()
            executorService = executorServiceFactory()
            resources.keep(AutoClosableScheduledExecutorService(executorService))
            future.complete(Unit)
            return future
        }

        @Volatile
        private lateinit var executorService: ScheduledExecutorService

        private fun fromConfig(config: Config): HeartbeatManagerConfig {
            return HeartbeatManagerConfig(Duration.ofMillis(config.getLong(LinkManagerConfiguration.HEARTBEAT_MESSAGE_PERIOD_KEY)),
            Duration.ofMillis(config.getLong(LinkManagerConfiguration.SESSION_TIMEOUT_KEY)))
        }

        private val trackedSessions = ConcurrentHashMap<String, TrackedSession>()

        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
            PublisherConfig(HEARTBEAT_MANAGER_CLIENT_ID),
            configuration
        )

        override val dominoTile = ComplexDominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            ::createResources,
            dependentChildren = setOf(groups.dominoTile, members.dominoTile, publisher.dominoTile),
            managedChildren = setOf(publisher.dominoTile),
            configurationChangeHandler = HeartbeatManagerConfigChangeHandler(),
        )

        /**
         * Calculates a weight for a Session.
         * Sessions for which an acknowledgement was recently received have a small weight.
         */
        fun calculateWeightForSession(sessionId: String): Long? {
            return trackedSessions[sessionId]?.lastAckTimestamp?.let { timeStamp() - it }
        }

        /**
         * For each Session we track the following.
         * [identityData]: The source and destination identities for this Session.
         * [lastSendTimestamp]: The last time we sent a message using this Session.
         * [lastAckTimestamp]: The last time a message we sent via this Session was acknowledged by the other side.
         * [sendingHeartbeats]: If true we send heartbeats to the counterparty (this happens after the session established).
         */
        class TrackedSession(
            val identityData: SessionCounterparties,
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

        fun sessionMessageSent(counterparties: SessionCounterparties, sessionId: String) {
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
                            { sessionTimeout(counterparties, sessionId) },
                            config.get().sessionTimeout.toMillis(),
                            TimeUnit.MILLISECONDS
                        )
                        TrackedSession(counterparties, timeStamp(), timeStamp())
                    }
                }
            }
        }

        fun startSendingHeartbeats(session: Session) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was sent before the HeartbeatManager was started.")
                }
                trackedSessions.computeIfPresent(session.sessionId) { _, trackedSession ->
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

        fun dataMessageSent(session: Session) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was sent before the HeartbeatManager was started.")
                }
                trackedSessions.computeIfPresent(session.sessionId) { _, trackedSession ->
                    trackedSession.lastSendTimestamp = timeStamp()
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

        private fun sessionTimeout(counterparties: SessionCounterparties, sessionId: String) {
            val sessionInfo = trackedSessions[sessionId] ?: return
            val timeSinceLastAck = timeStamp() - sessionInfo.lastAckTimestamp
            if (timeSinceLastAck >= config.get().sessionTimeout.toMillis()) {
                logger.info("Outbound session $sessionId (local=${counterparties.ourId}, remote=${counterparties.counterpartyId}) timed " +
                        "out due to inactivity and it will be cleaned up.")
                destroySession(counterparties, sessionId)
                trackedSessions.remove(sessionId)
            } else {
                executorService.schedule(
                    { sessionTimeout(counterparties, sessionId) },
                    config.get().sessionTimeout.toMillis() - timeSinceLastAck,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun sendHeartbeat(counterparties: SessionCounterparties, session: Session) {
            val sessionInfo = trackedSessions[session.sessionId]
            if (sessionInfo == null) {
                logger.info("Stopped sending heartbeats for session (${session.sessionId}), which expired.")
                return
            }
            val config = config.get()

            val timeSinceLastSend = timeStamp() - sessionInfo.lastSendTimestamp
            if (timeSinceLastSend >= config.heartbeatPeriod.toMillis()) {
                logger.trace { "Sending heartbeat message between ${counterparties.ourId} (our Identity) and " +
                    "${counterparties.counterpartyId}." }
                sendHeartbeatMessage(
                    counterparties.ourId.toHoldingIdentity(),
                    counterparties.counterpartyId.toHoldingIdentity(),
                    session,
                )
                executorService.schedule(
                    { sendHeartbeat(counterparties, session) },
                    config.heartbeatPeriod.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            } else {
                executorService.schedule(
                    { sendHeartbeat(counterparties, session) },
                    config.heartbeatPeriod.toMillis() - timeSinceLastSend,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun sendHeartbeatMessage(source: HoldingIdentity, dest: HoldingIdentity, session: Session) {
            val heartbeatMessage = HeartbeatMessage()
            val message = MessageConverter.linkOutMessageFromHeartbeat(source, dest, heartbeatMessage, session, groups, members)
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
            return clock.instant().toEpochMilli()
        }
    }
}
