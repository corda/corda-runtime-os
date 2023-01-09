package net.corda.p2p.linkmanager.sessions

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.CORDA_4
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD_EV3
import net.corda.membership.read.MembershipGroupReaderProvider
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
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.RevocationCheckMode
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.getSignatureSpec
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.common.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.grouppolicy.networkType
import net.corda.p2p.linkmanager.grouppolicy.protocolModes
import net.corda.p2p.linkmanager.inbound.InboundAssignmentListener
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.membership.lookupByKey
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionCounterparties
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindGroupInfo
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.ourHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.ourIdNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.peerHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.peerNotInTheMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.validationFailedWarning
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.Clock
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
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

@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
internal class SessionManagerImpl(
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val cryptoOpsClient: CryptoOpsClient,
    private val pendingOutboundSessionMessageQueues: PendingSessionMessageQueues,
    publisherFactory: PublisherFactory,
    private val configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val protocolFactory: ProtocolFactory = CryptoProtocolFactory(),
    private val clock: Clock,
    private val sessionReplayer: InMemorySessionReplayer = InMemorySessionReplayer(
        publisherFactory,
        configurationReaderService,
        coordinatorFactory,
        messagingConfiguration,
        groupPolicyProvider,
        membershipGroupReaderProvider,
        clock,
    ),

    executorServiceFactory: () -> ScheduledExecutorService = { Executors.newSingleThreadScheduledExecutor() },
) : SessionManager {

    companion object {
        fun getSessionCounterpartiesFromMessage(message: AuthenticatedMessage): SessionCounterparties {
            val peer = message.header.destination
            val us = message.header.source
            return SessionCounterparties(us.toCorda(), peer.toCorda())
        }
        private const val SESSION_MANAGER_CLIENT_ID = "session-manager"
    }

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Pair<SessionCounterparties, Session>>()

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    private val sessionNegotiationLock = ReentrantReadWriteLock()

    // This default needs to be removed and the lifecycle dependency graph adjusted to ensure the inbound subscription starts only after
    // the configuration has been received and the session manager has started (see CORE-6730).
    private val config = AtomicReference(
        SessionManagerConfig(1000000, 4, RevocationCheckMode.OFF, 432000)
    )

    private val heartbeatManager: HeartbeatManager = HeartbeatManager(
        publisherFactory,
        configurationReaderService,
        coordinatorFactory,
        messagingConfiguration,
        groupPolicyProvider,
        membershipGroupReaderProvider,
        ::refreshOutboundSession,
        clock,
        executorServiceFactory
    )
    private val outboundSessionPool = OutboundSessionPool(heartbeatManager::calculateWeightForSession)

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(SESSION_MANAGER_CLIENT_ID, false),
        messagingConfiguration
    )

    private val revocationCheckerClient = RevocationCheckerClient(publisherFactory, coordinatorFactory, messagingConfiguration)
    private val executorService = executorServiceFactory()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        ::onTileStart,
        onClose = { executorService.shutdownNow() },
        dependentChildren = setOf(
            heartbeatManager.dominoTile.coordinatorName, sessionReplayer.dominoTile.coordinatorName,
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            pendingOutboundSessionMessageQueues.dominoTile.coordinatorName, publisher.dominoTile.coordinatorName,
            linkManagerHostingMap.dominoTile.coordinatorName, inboundAssignmentListener.dominoTile.coordinatorName,
            revocationCheckerClient.dominoTile.coordinatorName,
        ),
        managedChildren = setOf(heartbeatManager.dominoTile.toNamedLifecycle(), sessionReplayer.dominoTile.toNamedLifecycle(),
            publisher.dominoTile.toNamedLifecycle(), revocationCheckerClient.dominoTile.toNamedLifecycle()),
        configurationChangeHandler = SessionManagerConfigChangeHandler()
    )

    @VisibleForTesting
    internal data class SessionManagerConfig(
        val maxMessageSize: Int,
        val sessionsPerCounterparties: Int,
        val revocationConfigMode: RevocationCheckMode,
        val sessionRefreshThreshold: Int,
    )

    internal inner class SessionManagerConfigChangeHandler : ConfigurationChangeHandler<SessionManagerConfig>(
        configurationReaderService,
        ConfigKeys.P2P_LINK_MANAGER_CONFIG,
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
                    val tombstoneRecords = (
                        outboundSessionPool.getAllSessionIds() + activeInboundSessions.keys +
                            pendingInboundSessions.keys
                        ).map { Record(SESSION_OUT_PARTITIONS, it, null) }
                    outboundSessionPool.clearPool()
                    activeInboundSessions.clear()
                    pendingInboundSessions.clear()
                    // This is suboptimal we could instead restart session negotiation
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
            config.getInt(LinkManagerConfiguration.SESSIONS_PER_PEER_KEY),
            config.getEnum(RevocationCheckMode::class.java, LinkManagerConfiguration.REVOCATION_CHECK_KEY),
            config.getInt(LinkManagerConfiguration.SESSION_REFRESH_THRESHOLD_KEY),
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

    private fun dataMessageSent(session: Session) {
        dominoTile.withLifecycleLock {
            heartbeatManager.dataMessageSent(session)
        }
    }

    override fun messageAcknowledged(sessionId: String) {
        dominoTile.withLifecycleLock {
            heartbeatManager.messageAcknowledged(sessionId)
        }
    }

    override fun recordsForSessionEstablished(
        session: Session,
        messageAndKey: AuthenticatedMessageAndKey,
    ): List<Record<String, *>> {
        return MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(
            messageAndKey,
            session,
            groupPolicyProvider,
            membershipGroupReaderProvider,
        )?.let { message ->
            val key = LinkManager.generateKey()
            val messageRecord = Record(LINK_OUT_TOPIC, key, message)
            val marker = AppMessageMarker(LinkManagerSentMarker(), clock.instant().toEpochMilli())
            val markerRecord = Record(P2P_OUT_MARKERS, messageAndKey.message.header.messageId, marker)
            dataMessageSent(session)
            listOf(
                messageRecord,
                markerRecord,
            )
        } ?: emptyList()
    }

    private fun onTileStart() {
        inboundAssignmentListener.registerCallbackForTopic { partitions ->
            val sessionIds = outboundSessionPool.getAllSessionIds() + pendingInboundSessions.keys + activeInboundSessions.keys
            val records = sessionIds.map { sessionId ->
                Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(partitions.toList()))
            }
            if (records.isNotEmpty()) publisher.publish(records)
        }
    }

    private fun refreshOutboundSession(counterparties: SessionCounterparties, sessionId: String) {
        sessionNegotiationLock.write {
            sessionReplayer.removeMessageFromReplay(initiatorHandshakeUniqueId(sessionId), counterparties)
            sessionReplayer.removeMessageFromReplay(initiatorHelloUniqueId(sessionId), counterparties)
            val sessionInitMessage = genSessionInitMessages(counterparties, 1)
            if(sessionInitMessage.isEmpty()) {
                outboundSessionPool.removeSessions(counterparties)
                return
            }
            if (!outboundSessionPool.replaceSession(sessionId, sessionInitMessage.single().first)) {
                // If the session was not replaced do not send a initiatorHello
                return
            }
            val records = linkOutMessagesFromSessionInitMessages(counterparties, sessionInitMessage) ?.let {
                OutboundMessageProcessor.recordsForNewSessions(
                    SessionState.NewSessionsNeeded(it),
                    inboundAssignmentListener,
                    logger
                ) + listOf(Record(SESSION_OUT_PARTITIONS, sessionId, null))
            }
            records?.let { publisher.publish(records) }
        }
    }

    private fun initiatorHelloUniqueId(sessionId: String): String {
        return sessionId + "_" + InitiatorHelloMessage::class.java.simpleName
    }

    private fun initiatorHandshakeUniqueId(sessionId: String): String {
        return sessionId + "_" + InitiatorHandshakeMessage::class.java.simpleName
    }

    private fun genSessionInitMessages(
        counterparties: SessionCounterparties,
        multiplicity: Int
    ): List<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>> {

        val groupPolicy = groupPolicyProvider.getGroupPolicy(counterparties.ourId)
        if (groupPolicy == null) {
            logger.warn(
                "Could not find the group information in the GroupPolicyProvider for ${counterparties.ourId}." +
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
        val pkiMode = pkiMode(groupPolicy, sessionManagerConfig) ?: return emptyList()
        (1..multiplicity).map {
            val sessionId = UUID.randomUUID().toString()
            val session = protocolFactory.createInitiator(
                sessionId,
                groupPolicy.protocolModes,
                sessionManagerConfig.maxMessageSize,
                ourIdentityInfo.sessionPublicKey,
                ourIdentityInfo.holdingIdentity.groupId,
                pkiMode
            )
            messagesAndProtocol.add(Pair(session, session.generateInitiatorHello()))
        }
        return messagesAndProtocol
    }

    private fun pkiMode(
        groupPolicy: GroupPolicy,
        sessionManagerConfig: SessionManagerConfig
    ): CertificateCheckMode? {
        return when (groupPolicy.p2pParameters.sessionPki) {
            STANDARD -> {
                val trustedCertificates = groupPolicy.p2pParameters.sessionTrustRoots?.toList()

                if (trustedCertificates == null) {
                    logger.error("Expected session trust stores to be in group policy for group ${groupPolicy.groupId}.")
                    return null
                }
                CertificateCheckMode.CheckCertificate(
                    trustedCertificates,
                    sessionManagerConfig.revocationConfigMode,
                    revocationCheckerClient::checkRevocation
                )
            }
            STANDARD_EV3, CORDA_4 -> {
                logger.error("PkiMode ${groupPolicy.p2pParameters.sessionPki} is unsupported by the link manager.")
                return null
            }
            NO_PKI -> CertificateCheckMode.NoCertificate
        }
    }

    private fun linkOutMessagesFromSessionInitMessages(
        counterparties: SessionCounterparties,
        messages: List<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>>
    ): List<Pair<String, LinkOutMessage>>? {
        val sessionIds = messages.map { it.first.sessionId }
        logger.info(
            "Local identity (${counterparties.ourId}) initiating new sessions with Ids $sessionIds with remote identity " +
                "${counterparties.counterpartyId}"
        )

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

        val responderMemberInfo = membershipGroupReaderProvider.lookup(counterparties.ourId, counterparties.counterpartyId)
        if (responderMemberInfo == null) {
            logger.warn("Attempted to start session negotiation with peer ${counterparties.counterpartyId} which is not in " +
                "${counterparties.ourId}'s members map. The sessionInit message was not sent.")
            return null
        }

        val groupPolicy = groupPolicyProvider.getGroupPolicy(counterparties.ourId)
        if (groupPolicy == null) {
            logger.warn(
                "Could not find the group information in the GroupPolicyProvider for ${counterparties.ourId}." +
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
                    createLinkOutMessage(message.second, counterparties.ourId, responderMemberInfo, groupPolicy.networkType)
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

        val responderMemberInfo = membershipGroupReaderProvider
            .lookup(sessionInfo.ourId, sessionInfo.counterpartyId)
        if (responderMemberInfo == null) {
            logger.peerNotInTheMembersMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.counterpartyId)
            return null
        }

        val tenantId = ourIdentityInfo.sessionKeyTenantId

        val signWithOurGroupId = { data: ByteArray ->
            cryptoOpsClient.sign(
                tenantId,
                ourIdentityInfo.sessionPublicKey,
                ourIdentityInfo.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                data
            ).bytes
        }
        val payload = try {
            session.generateOurHandshakeMessage(
                responderMemberInfo.sessionInitiationKey,
                ourIdentityInfo.sessionCertificates,
                signWithOurGroupId
            )
        } catch (exception: Exception) {
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

        val groupPolicy = groupPolicyProvider.getGroupPolicy(ourIdentityInfo.holdingIdentity)
        if (groupPolicy == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity)
            return null
        }
        heartbeatManager.sessionMessageSent(
            SessionCounterparties(ourIdentityInfo.holdingIdentity, responderMemberInfo.holdingIdentity),
            message.header.sessionId,
        )

        return createLinkOutMessage(payload, sessionInfo.ourId, responderMemberInfo, groupPolicy.networkType)
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

        val memberInfo = membershipGroupReaderProvider
            .lookup(sessionCounterparties.ourId, sessionCounterparties.counterpartyId)
        if (memberInfo == null) {
            logger.peerNotInTheMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                sessionCounterparties.counterpartyId
            )
            return null
        }

        if (!session.validatePeerHandshakeMessageHandleError(message, memberInfo, sessionCounterparties)) {
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
            )
        }
        logger.info(
            "Outbound session ${authenticatedSession.sessionId} established " +
                "(local=${sessionCounterparties.ourId}, remote=${sessionCounterparties.counterpartyId})."
        )
        val sessionManagerConfig = config.get()
        executorService.schedule(
            { refreshSessionAndLog(sessionCounterparties, message.header.sessionId) },
            sessionManagerConfig.sessionRefreshThreshold.toLong(),
            TimeUnit.SECONDS
        )
        return null
    }

    private fun refreshSessionAndLog(sessionCounterparties: SessionCounterparties, sessionId: String) {
        logger.info(
            "Outbound session $sessionId (local=${sessionCounterparties.ourId}, remote=${sessionCounterparties.counterpartyId}) timed " +
                    "out to refresh ephemeral keys and it will be cleaned up."
        )
        refreshOutboundSession(sessionCounterparties, sessionId) 
        heartbeatManager.stopTrackingSpecifiedSession(sessionId)
    }

    private fun processInitiatorHello(message: InitiatorHelloMessage): LinkOutMessage? {
        logger.info("Processing ${message::class.java.simpleName} for session ${message.header.sessionId}.")
        //This will be adjusted so that we use the group policy coming from the CPI with the latest version deployed locally (CORE-5323).
        val hostedIdentitiesInSameGroup = linkManagerHostingMap.allLocallyHostedIdentities()
            .filter { it.groupId == message.source.groupId }
        if (hostedIdentitiesInSameGroup.isEmpty()) {
            logger.warn("There is no locally hosted identity in group ${message.source.groupId}. The initiator message was discarded.")
            return null
        }

        val sessionManagerConfig = config.get()
        val (hostedIdentityInSameGroup, peer) = hostedIdentitiesInSameGroup
            .firstNotNullOfOrNull { hostedIdentityInSameGroup ->
                val member = membershipGroupReaderProvider
                    .lookupByKey(hostedIdentityInSameGroup, message.source.initiatorPublicKeyHash.array())
                if (member == null) {
                    null
                } else {
                    hostedIdentityInSameGroup to member
                }
            } ?: let {
            logger.peerHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                message.source.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }

        val groupPolicy = groupPolicyProvider.getGroupPolicy(hostedIdentityInSameGroup)
        if (groupPolicy == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, hostedIdentityInSameGroup)
            return null
        }
        val pkiMode = pkiMode(groupPolicy, sessionManagerConfig) ?: return null

        val session = pendingInboundSessions.computeIfAbsent(message.header.sessionId) { sessionId ->
            val session = protocolFactory.createResponder(
                sessionId,
                groupPolicy.protocolModes,
                sessionManagerConfig.maxMessageSize,
                pkiMode
            )
            session.receiveInitiatorHello(message)
            session
        }
        val responderHello = session.generateResponderHello()

        logger.info("Remote identity ${peer.holdingIdentity} initiated new session ${message.header.sessionId}.")
        return createLinkOutMessage(responderHello, hostedIdentityInSameGroup, peer, groupPolicy.networkType)
    }

    private fun processInitiatorHandshake(message: InitiatorHandshakeMessage): LinkOutMessage? {
        logger.info("Processing ${message::class.java.simpleName} for session ${message.header.sessionId}.")
        val session = pendingInboundSessions[message.header.sessionId]
        if (session == null) {
            logger.noSessionWarning(message::class.java.simpleName, message.header.sessionId)
            return null
        }

        val initiatorIdentityData = session.getInitiatorIdentity()
        val hostedIdentitiesInSameGroup = linkManagerHostingMap.allLocallyHostedIdentities()
            .filter { it.groupId == initiatorIdentityData.groupId }
        if (hostedIdentitiesInSameGroup.isEmpty()) {
            logger.warn("There is no locally hosted identity in group ${initiatorIdentityData.groupId}. The initiator handshake message" +
                    " was discarded.")
            return null
        }

        val peer = hostedIdentitiesInSameGroup
            .firstNotNullOfOrNull { hostedIdentityInSameGroup ->
                membershipGroupReaderProvider
                    .lookupByKey(hostedIdentityInSameGroup, initiatorIdentityData.initiatorPublicKeyHash.array())
            }
        if (peer == null) {
            logger.peerHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                initiatorIdentityData.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }

        session.generateHandshakeSecrets()
        val ourIdentityData = session.validatePeerHandshakeMessageHandleError(message, peer,) ?: return null
        // Find the correct Holding Identity to use (using the public key hash).
        val ourIdentityInfo = linkManagerHostingMap.getInfo(ourIdentityData.responderPublicKeyHash, ourIdentityData.groupId)
        if (ourIdentityInfo == null) {
            logger.ourHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                ourIdentityData.responderPublicKeyHash.toBase64()
            )
            return null
        }

        val groupPolicy = groupPolicyProvider.getGroupPolicy(ourIdentityInfo.holdingIdentity)
        if (groupPolicy == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity)
            return null
        }

        val tenantId = ourIdentityInfo.sessionKeyTenantId

        val response = try {
            val ourPublicKey = ourIdentityInfo.sessionPublicKey
            val signData = { data: ByteArray ->
                cryptoOpsClient.sign(
                    tenantId,
                    ourIdentityInfo.sessionPublicKey,
                    ourIdentityInfo.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                    data
                ).bytes
            }
            session.generateOurHandshakeMessage(ourPublicKey, ourIdentityInfo.sessionCertificates, signData)
        } catch (exception: Exception) {
            logger.warn(
                "Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                    " The message was discarded."
            )
            return null
        }

        activeInboundSessions[message.header.sessionId] = Pair(
            SessionCounterparties(ourIdentityInfo.holdingIdentity, peer.holdingIdentity),
            session.getSession()
        )
        logger.info(
            "Inbound session ${message.header.sessionId} established " +
                "(local=${ourIdentityInfo.holdingIdentity}, remote=${peer.holdingIdentity})."
        )
        /**
         * We delay removing the session from pendingInboundSessions until we receive the first data message as before this point
         * the other side (Initiator) might replay [InitiatorHandshakeMessage] in the case where the [ResponderHandshakeMessage] was lost.
         * */
        return createLinkOutMessage(response, ourIdentityInfo.holdingIdentity, peer, groupPolicy.networkType)
    }

    private fun AuthenticationProtocolResponder.validatePeerHandshakeMessageHandleError(
        message: InitiatorHandshakeMessage,
        peer: MemberInfo
    ): HandshakeIdentityData? {
        return try {
            this.validatePeerHandshakeMessage(
                message,
                peer.holdingIdentity.x500Name,
                peer.sessionInitiationKey,
                peer.sessionInitiationKey.toKeyAlgorithm().getSignatureSpec(),
            )
        } catch (exception: WrongPublicKeyHashException) {
            logger.error("The message was discarded. ${exception.message}")
            null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            null
        } catch (exception: InvalidPeerCertificate) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            null
        }
    }

    private fun AuthenticationProtocolInitiator.validatePeerHandshakeMessageHandleError(
        message: ResponderHandshakeMessage,
        memberInfo: MemberInfo,
        sessionCounterparties: SessionCounterparties,
    ): Boolean {
        return try {
            this.validatePeerHandshakeMessage(
                message,
                sessionCounterparties.counterpartyId.x500Name,
                memberInfo.sessionInitiationKey,
                memberInfo.sessionInitiationKey.toKeyAlgorithm().getSignatureSpec(),
            )
            true
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            false
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            false
        } catch (exception: InvalidPeerCertificate) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            false
        }
    }

    class HeartbeatManager(
        publisherFactory: PublisherFactory,
        private val configurationReaderService: ConfigurationReadService,
        coordinatorFactory: LifecycleCoordinatorFactory,
        configuration: SmartConfig,
        private val groupPolicyProvider: GroupPolicyProvider,
        private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
        private val destroySession: (counterparties: SessionCounterparties, sessionId: String) -> Any,
        private val clock: Clock,
        executorServiceFactory: () -> ScheduledExecutorService
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
        internal inner class HeartbeatManagerConfigChangeHandler : ConfigurationChangeHandler<HeartbeatManagerConfig>(
            configurationReaderService,
            ConfigKeys.P2P_LINK_MANAGER_CONFIG,
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

        private val executorService = executorServiceFactory()

        private fun fromConfig(config: Config): HeartbeatManagerConfig {
            return HeartbeatManagerConfig(
                Duration.ofMillis(config.getLong(LinkManagerConfiguration.HEARTBEAT_MESSAGE_PERIOD_KEY)),
                Duration.ofMillis(config.getLong(LinkManagerConfiguration.SESSION_TIMEOUT_KEY))
            )
        }

        private val trackedSessions = ConcurrentHashMap<String, TrackedSession>()

        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
            PublisherConfig(HEARTBEAT_MANAGER_CLIENT_ID, false),
            configuration
        )

        override val dominoTile = ComplexDominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            onClose = { executorService.shutdownNow() },
            dependentChildren = setOf(
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                publisher.dominoTile.coordinatorName
            ),
            managedChildren = setOf(publisher.dominoTile.toNamedLifecycle()),
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

        fun stopTrackingSpecifiedSession(sessionId: String) {
            trackedSessions.remove(sessionId)
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
                logger.info(
                    "Outbound session $sessionId (local=${counterparties.ourId}, remote=${counterparties.counterpartyId}) timed " +
                        "out due to inactivity and it will be cleaned up."
                )
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
                logger.trace {
                    "Sending heartbeat message between ${counterparties.ourId} (our Identity) and " +
                        "${counterparties.counterpartyId}."
                }
                sendHeartbeatMessage(
                    counterparties.ourId,
                    counterparties.counterpartyId,
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
            val message = MessageConverter.linkOutMessageFromHeartbeat(
                source,
                dest,
                heartbeatMessage,
                session,
                groupPolicyProvider,
                membershipGroupReaderProvider
            )
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
