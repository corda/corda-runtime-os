package net.corda.p2p.linkmanager.sessions

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.HeartbeatMessage
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.SessionPartitions
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerSentMarker
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
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.CORDA_4
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.STANDARD_EV3
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.metrics.CordaMetrics.Metric.InboundSessionCount
import net.corda.metrics.CordaMetrics.Metric.OutboundSessionCount
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import net.corda.p2p.crypto.protocol.api.HandshakeIdentityData
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeResponderKeyHash
import net.corda.p2p.crypto.protocol.api.InvalidPeerCertificate
import net.corda.p2p.crypto.protocol.api.RevocationCheckMode
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.common.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.getSignatureSpec
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.linkmanager.delivery.InMemorySessionReplayer
import net.corda.p2p.linkmanager.grouppolicy.networkType
import net.corda.p2p.linkmanager.grouppolicy.protocolModes
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.inbound.InboundAssignmentListener
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.membership.lookupByKey
import net.corda.p2p.linkmanager.outbound.OutboundMessageProcessor
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionCounterparties
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindGroupInfo
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindSessionInformation
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.noSessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.ourHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.ourIdNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.peerHashNotInMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.peerNotInTheMembersMapWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.validationFailedWarning
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.SESSION_OUT_PARTITIONS
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.time.Clock
import net.corda.utilities.trace
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.p2p.crypto.protocol.api.InvalidSelectedModeError
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.badGroupPolicy
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
        clock
    ),

    executorServiceFactory: () -> ScheduledExecutorService = Executors::newSingleThreadScheduledExecutor
) : SessionManager {

    private companion object {
        private const val SESSION_MANAGER_CLIENT_ID = "session-manager"
    }

    private val pendingInboundSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeInboundSessions = ConcurrentHashMap<String, Pair<SessionManager.Counterparties, Session>>()

    private val logger = LoggerFactory.getLogger(this::class.java)

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
        ::tearDownInboundSession,
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

    // These metrics must be removed on shutdown as the MeterRegistry holds references to their lambdas.
    private val outboundSessionCount = OutboundSessionCount { outboundSessionPool.getAllSessionIds().size }.builder().build()
    private val inboundSessionCount = InboundSessionCount { activeInboundSessions.size + pendingInboundSessions.size }.builder().build()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        ::onTileStart,
        ::onTileClose,
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

    private fun getSessionCounterpartiesFromMessage(message: AuthenticatedMessage): SessionCounterparties? {
        val peer = message.header.destination
        val us = message.header.source
        val status = message.header.statusFilter
        val info = membershipGroupReaderProvider.lookup(us.toCorda(), peer.toCorda(), status)
        if (info == null) {
            logger.couldNotFindSessionInformation(us.toCorda().shortHash, peer.toCorda().shortHash, message.header.messageId)
            return null
        }
        return SessionCounterparties(us.toCorda(), peer.toCorda(), status, info.serial)
    }

    override fun processOutboundMessage(message: AuthenticatedMessageAndKey): SessionState {
        return dominoTile.withLifecycleLock {
            sessionNegotiationLock.read {
                val counterparties = getSessionCounterpartiesFromMessage(message.message)
                    ?: return@read SessionState.CannotEstablishSession

                return@read when (val status = outboundSessionPool.getNextSession(counterparties)) {
                    is OutboundSessionPool.SessionPoolStatus.SessionActive ->
                        SessionState.SessionEstablished(status.session, counterparties)
                    is OutboundSessionPool.SessionPoolStatus.SessionPending -> {
                        SessionState.SessionAlreadyPending(counterparties)
                    }
                    is OutboundSessionPool.SessionPoolStatus.NewSessionsNeeded -> {
                        val initMessages = genSessionInitMessages(counterparties, config.get().sessionsPerCounterparties)
                        if (initMessages.isEmpty()) return@read SessionState.CannotEstablishSession
                        outboundSessionPool.addPendingSessions(counterparties, initMessages.map { it.first })
                        val messages = linkOutMessagesFromSessionInitMessages(
                            counterparties,
                            initMessages,
                            message.message.header.statusFilter
                        ) ?: return@read SessionState.CannotEstablishSession
                        SessionState.NewSessionsNeeded(messages, counterparties)
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
                SessionManager.SessionDirection.Outbound(
                    SessionManager.Counterparties(
                        outboundSession.sessionCounterparties.ourId,
                        outboundSession.sessionCounterparties.counterpartyId
                    ),
                    outboundSession.session
                )
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

    override fun sessionMessageReceived(sessionId: String) {
        dominoTile.withLifecycleLock {
            heartbeatManager.sessionMessageReceived(sessionId)
        }
    }

    override fun dataMessageReceived(sessionId: String) {
        dominoTile.withLifecycleLock {
            heartbeatManager.dataMessageReceived(sessionId)
        }
    }

    override fun recordsForSessionEstablished(
        session: Session,
        messageAndKey: AuthenticatedMessageAndKey,
        serial: Long,
    ): List<Record<String, *>> {
        return MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(
            messageAndKey,
            session,
            groupPolicyProvider,
            membershipGroupReaderProvider,
            serial
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

    private fun onTileClose() {
        executorService.shutdownNow()
        CordaMetrics.registry.remove(inboundSessionCount)
        CordaMetrics.registry.remove(outboundSessionCount)
    }

    private fun tearDownInboundSession(sessionId: String) {
        activeInboundSessions.remove(sessionId)
        pendingInboundSessions.remove(sessionId)
    }

    private fun refreshOutboundSession(sessionCounterparties: SessionCounterparties, sessionId: String) {
        sessionNegotiationLock.write {
            sessionReplayer.removeMessageFromReplay(initiatorHandshakeUniqueId(sessionId), sessionCounterparties)
            sessionReplayer.removeMessageFromReplay(initiatorHelloUniqueId(sessionId), sessionCounterparties)
            val sessionInitMessage = genSessionInitMessages(sessionCounterparties, 1)
            if(sessionInitMessage.isEmpty()) {
                outboundSessionPool.removeSessions(sessionCounterparties)
                return
            }
            if (!outboundSessionPool.replaceSession(sessionCounterparties, sessionId, sessionInitMessage.single().first)) {
                // If the session was not replaced do not send a initiatorHello
                return
            }
            val records = linkOutMessagesFromSessionInitMessages(
                sessionCounterparties, sessionInitMessage, sessionCounterparties.status
            ) ?.let {
                OutboundMessageProcessor.recordsForNewSessions(
                    SessionState.NewSessionsNeeded(it, sessionCounterparties),
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

        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(counterparties.ourId)
        } catch (except: BadGroupPolicyException) {
            logger.warn("The group policy data is unavailable or cannot be parsed for ${counterparties.ourId}. Error: ${except.message}. " +
                "The sessionInit message was not sent.")
            return emptyList()
        }
        if (p2pParams == null) {
            logger.warn(
                "Could not find the p2p parameters in the GroupPolicyProvider for ${counterparties.ourId}." +
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
        val pkiMode = pkiMode(p2pParams, sessionManagerConfig) ?: return emptyList()
        (1..multiplicity).map {
            val sessionId = UUID.randomUUID().toString()
            val session = protocolFactory.createInitiator(
                sessionId,
                p2pParams.protocolModes,
                sessionManagerConfig.maxMessageSize,
                ourIdentityInfo.preferredSessionKeyAndCertificates.sessionPublicKey,
                ourIdentityInfo.holdingIdentity.groupId,
                pkiMode
            )
            messagesAndProtocol.add(Pair(session, session.generateInitiatorHello()))
        }
        return messagesAndProtocol
    }

    private fun pkiMode(
        p2pParameters: GroupPolicy.P2PParameters,
        sessionManagerConfig: SessionManagerConfig
    ): CertificateCheckMode? {
        return when (p2pParameters.sessionPki) {
            STANDARD -> {
                val trustedCertificates = p2pParameters.sessionTrustRoots?.toList()

                if (trustedCertificates == null) {
                    logger.error("Expected session trust stores to be in p2p parameters ${p2pParameters}.")
                    return null
                }
                CertificateCheckMode.CheckCertificate(
                    trustedCertificates,
                    sessionManagerConfig.revocationConfigMode,
                    revocationCheckerClient::checkRevocation
                )
            }
            STANDARD_EV3, CORDA_4 -> {
                logger.error("PkiMode ${p2pParameters.sessionPki} is unsupported by the link manager.")
                return null
            }
            NO_PKI -> CertificateCheckMode.NoCertificate
        }
    }

    private fun linkOutMessagesFromSessionInitMessages(
        sessionCounterparties: SessionCounterparties,
        messages: List<Pair<AuthenticationProtocolInitiator, InitiatorHelloMessage>>,
        filter: MembershipStatusFilter
    ): List<Pair<String, LinkOutMessage>>? {
        val sessionIds = messages.map { it.first.sessionId }
        logger.info(
            "Local identity (${sessionCounterparties.ourId}) initiating new sessions with Ids $sessionIds with remote identity " +
                "${sessionCounterparties.counterpartyId}"
        )

        val responderMemberInfo = membershipGroupReaderProvider.lookup(
            sessionCounterparties.ourId,
            sessionCounterparties.counterpartyId,
            filter
        )
        if (responderMemberInfo != null && responderMemberInfo.serial > sessionCounterparties.serial) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionCounterparties.counterpartyId} which is " +
                        "in ${sessionCounterparties.ourId}'s members map but the serial number has progressed beyond " +
                        "the requested serial number. The sessionInit message was not sent and will not be retried."
            )
            return null
        }

        for (message in messages) {
            sessionReplayer.addMessageForReplay(
                initiatorHelloUniqueId(message.first.sessionId),
                InMemorySessionReplayer.SessionMessageReplay(
                    message.second,
                    message.first.sessionId,
                    sessionCounterparties,
                    heartbeatManager::sessionMessageSent
                ),
                sessionCounterparties
            )
        }

        if (responderMemberInfo == null) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionCounterparties.counterpartyId} which is " +
                        "not in ${sessionCounterparties.ourId}'s members map. Filter was $filter. The sessionInit " +
                        "message was not sent. Message will be retried."
            )
            return null
        } else if (responderMemberInfo.serial < sessionCounterparties.serial) {
            logger.warn(
                "Attempted to start session negotiation with peer ${sessionCounterparties.counterpartyId} which is " +
                        "not in ${sessionCounterparties.ourId}'s members map with serial " +
                        "${sessionCounterparties.serial}. Filter was $filter and serial found was " +
                        "${responderMemberInfo.serial}. The sessionInit message was not sent. Message will be retried."
            )
            return null
        }

        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(sessionCounterparties.ourId)
        } catch (except: BadGroupPolicyException) {
            logger.warn("The group policy data is unavailable or cannot be parsed for ${sessionCounterparties.ourId}. Error: " +
                "${except.message}. The sessionInit message was not sent.")
            return emptyList()
        }
        if (p2pParams == null) {
            logger.warn(
                "Could not find the group information in the GroupPolicyProvider for ${sessionCounterparties.ourId}." +
                    " The sessionInit message was not sent."
            )
            return emptyList()
        }

        val linkOutMessages = mutableListOf<Pair<String, LinkOutMessage>>()
        for (message in messages) {
            heartbeatManager.sessionMessageSent(sessionCounterparties, message.first.sessionId)
            linkOutMessages.add(
                Pair(
                    message.first.sessionId,
                    createLinkOutMessage(message.second, sessionCounterparties.ourId, responderMemberInfo, p2pParams.networkType)
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
            .lookup(sessionInfo.ourId, sessionInfo.counterpartyId, sessionInfo.status)
        if (responderMemberInfo == null) {
            logger.peerNotInTheMembersMapWarning(message::class.java.simpleName, message.header.sessionId, sessionInfo.counterpartyId)
            return null
        }

        val tenantId = ourIdentityInfo.holdingIdentity.shortHash.value
        val ourIdentitySessionKey = ourIdentityInfo.preferredSessionKeyAndCertificates

        val signWithOurGroupId = { data: ByteArray ->
            cryptoOpsClient.sign(
                tenantId,
                ourIdentitySessionKey.sessionPublicKey,
                ourIdentitySessionKey.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                data
            ).bytes
        }
        val payload = try {
            session.generateOurHandshakeMessage(
                responderMemberInfo.sessionInitiationKeys.first(),
                ourIdentitySessionKey.sessionCertificateChain,
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
                sessionInfo,
                heartbeatManager::sessionMessageSent
            ),
            sessionInfo
        )

        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(ourIdentityInfo.holdingIdentity)
        } catch (except: BadGroupPolicyException) {
            logger.badGroupPolicy(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity, except.message)
            return null
        }
        if (p2pParams == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity)
            return null
        }
        heartbeatManager.sessionMessageSent(
            sessionInfo,
            message.header.sessionId,
        )

        return createLinkOutMessage(payload, sessionInfo.ourId, responderMemberInfo, p2pParams.networkType)
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

        val memberInfo = membershipGroupReaderProvider.lookup(
            sessionCounterparties.ourId, sessionCounterparties.counterpartyId, sessionCounterparties.status
        )
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
        heartbeatManager.stopTrackingSpecifiedOutboundSession(sessionId)
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
        val locallyHostedIdentityWithPeerMemberInfo = hostedIdentitiesInSameGroup.mapNotNull { localIdentity ->
            val peerMemberInfo = membershipGroupReaderProvider.lookupByKey(
                localIdentity,
                message.source.initiatorPublicKeyHash.array(),
                MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING
            )
            if (peerMemberInfo == null) {
                null
            } else {
                localIdentity to peerMemberInfo
            }
        }.maxByOrNull { it.second.serial }

        if (locallyHostedIdentityWithPeerMemberInfo == null) {
            logger.peerHashNotInMembersMapWarning(
                message::class.java.simpleName,
                message.header.sessionId,
                message.source.initiatorPublicKeyHash.array().toBase64()
            )
            return null
        }

        val (hostedIdentityInSameGroup, peerMemberInfo) = locallyHostedIdentityWithPeerMemberInfo
        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(hostedIdentityInSameGroup)
        } catch (except: BadGroupPolicyException) {
            logger.badGroupPolicy(message::class.java.simpleName, message.header.sessionId, hostedIdentityInSameGroup, except.message)
            return null
        }
        if (p2pParams == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, hostedIdentityInSameGroup)
            return null
        }

        val session = pendingInboundSessions.computeIfAbsent(message.header.sessionId) { sessionId ->
            val session = protocolFactory.createResponder(sessionId, sessionManagerConfig.maxMessageSize)
            session.receiveInitiatorHello(message)
            session
        }
        val responderHello = session.generateResponderHello()

        logger.info("Remote identity ${peerMemberInfo.holdingIdentity} initiated new session ${message.header.sessionId}.")
        return createLinkOutMessage(responderHello, hostedIdentityInSameGroup, peerMemberInfo, p2pParams.networkType)
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
                    .lookupByKey(
                        hostedIdentityInSameGroup,
                        initiatorIdentityData.initiatorPublicKeyHash.array(),
                        MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING,
                    )
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
        val ourIdentityData = session.validatePeerHandshakeMessageHandleError(message, peer) ?: return null
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

        val p2pParams = try {
            groupPolicyProvider.getP2PParameters(ourIdentityInfo.holdingIdentity)
        } catch (except: BadGroupPolicyException) {
            logger.badGroupPolicy(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity, except.message)
            return null
        }
        if (p2pParams == null) {
            logger.couldNotFindGroupInfo(message::class.java.simpleName, message.header.sessionId, ourIdentityInfo.holdingIdentity)
            return null
        }

        val sessionManagerConfig = config.get()
        val pkiMode = pkiMode(p2pParams, sessionManagerConfig) ?: return null
        try {
            session.validateEncryptedExtensions(pkiMode, p2pParams.protocolModes, peer.holdingIdentity.x500Name)
        } catch (exception: InvalidPeerCertificate) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        } catch (exception: NoCommonModeError) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            return null
        }

        val tenantId = ourIdentityInfo.holdingIdentity.shortHash.value
        val ourIdentitySessionKey = ourIdentityInfo.allSessionKeysAndCertificates.first {
            session.hash(it.sessionPublicKey).contentEquals(ourIdentityData.responderPublicKeyHash)
        }

        val response = try {
            val ourPublicKey = ourIdentitySessionKey.sessionPublicKey
            val signData = { data: ByteArray ->
                cryptoOpsClient.sign(
                    tenantId,
                    ourIdentitySessionKey.sessionPublicKey,
                    ourIdentitySessionKey.sessionPublicKey.toKeyAlgorithm().getSignatureSpec(),
                    data
                ).bytes
            }
            session.generateOurHandshakeMessage(ourPublicKey, ourIdentitySessionKey.sessionCertificateChain, signData)
        } catch (exception: Exception) {
            logger.warn(
                "Received ${message::class.java.simpleName} with sessionId ${message.header.sessionId}. ${exception.message}." +
                    " The message was discarded."
            )
            return null
        }

        activeInboundSessions[message.header.sessionId] = Pair(
            SessionManager.Counterparties(ourIdentityInfo.holdingIdentity, peer.holdingIdentity),
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
        return createLinkOutMessage(response, ourIdentityInfo.holdingIdentity, peer, p2pParams.networkType)
    }

    private fun AuthenticationProtocolResponder.validatePeerHandshakeMessageHandleError(
        message: InitiatorHandshakeMessage,
        peer: MemberInfo
    ): HandshakeIdentityData? {
        return try {
            validatePeerHandshakeMessage(
                message,
                peer.sessionInitiationKeys.map { it to it.toKeyAlgorithm().getSignatureSpec() }
            )
        } catch (exception: WrongPublicKeyHashException) {
            logger.error("The message was discarded. ${exception.message}")
            null
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
            null
        }
    }

    private fun AuthenticationProtocolInitiator.validatePeerHandshakeMessageHandleError(
        message: ResponderHandshakeMessage,
        memberInfo: MemberInfo,
        sessionCounterparties: SessionCounterparties,
    ): Boolean {
        try {
            this.validatePeerHandshakeMessage(
                message,
                sessionCounterparties.counterpartyId.x500Name,
                memberInfo.sessionInitiationKeys.map {
                    it to it.toKeyAlgorithm().getSignatureSpec()
                },
            )
            return true
        } catch (exception: InvalidHandshakeResponderKeyHash) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        } catch (exception: InvalidHandshakeMessageException) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        } catch (exception: InvalidPeerCertificate) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        } catch (exception: InvalidSelectedModeError) {
            logger.validationFailedWarning(message::class.java.simpleName, message.header.sessionId, exception.message)
        }
        return false
    }

    class HeartbeatManager(
        publisherFactory: PublisherFactory,
        private val configurationReaderService: ConfigurationReadService,
        coordinatorFactory: LifecycleCoordinatorFactory,
        configuration: SmartConfig,
        private val groupPolicyProvider: GroupPolicyProvider,
        private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
        private val destroyOutboundSession: (counterparties: SessionCounterparties, sessionId: String) -> Any,
        private val destroyInboundSession: (sessionId: String) -> Unit,
        private val clock: Clock,
        executorServiceFactory: () -> ScheduledExecutorService
    ) : LifecycleWithDominoTile {

        companion object {
            private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

        private val trackedOutboundSessions = ConcurrentHashMap<String, TrackedOutboundSession>()
        private val trackedInboundSessions = ConcurrentHashMap<String, TrackedInboundSession>()

        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
            PublisherConfig(HEARTBEAT_MANAGER_CLIENT_ID, false),
            configuration
        )

        override val dominoTile = ComplexDominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            onClose = executorService::shutdownNow,
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
            return trackedOutboundSessions[sessionId]?.lastAckTimestamp?.let { timeStamp() - it }
        }

        /**
         * For each Outbound Session we track the following.
         * [identityData]: The source and destination identities for this Session.
         * [lastSendTimestamp]: The last time we sent a message using this Session.
         * [lastAckTimestamp]: The last time a message we sent via this Session was acknowledged by the other side.
         * [sendingHeartbeats]: If true we send heartbeats to the counterparty (this happens after the session established).
         */
        class TrackedOutboundSession(
            val identityData: SessionCounterparties,
            @Volatile
            var lastSendTimestamp: Long,
            @Volatile
            var lastAckTimestamp: Long,
            @Volatile
            var sendingHeartbeats: Boolean = false
        )

        /**
         * For each Inbound Session we track the following.
         * [lastReceivedTimestamp]: The last time we received a message using this Session.
         */
        class TrackedInboundSession(
            @Volatile
            var lastReceivedTimestamp: Long,
        )

        fun stopTrackingAllSessions() {
            trackedOutboundSessions.clear()
            trackedInboundSessions.clear()
        }

        fun stopTrackingSpecifiedOutboundSession(sessionId: String) {
            trackedOutboundSessions.remove(sessionId)
        }

        fun sessionMessageSent(counterparties: SessionCounterparties, sessionId: String) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A session message was added before the HeartbeatManager was started.")
                }
                trackedOutboundSessions.compute(sessionId) { _, initialTrackedSession ->
                    if (initialTrackedSession != null) {
                        initialTrackedSession.lastSendTimestamp = timeStamp()
                        initialTrackedSession
                    } else {
                        executorService.schedule(
                            { outboundSessionTimeout(counterparties, sessionId) },
                            config.get().sessionTimeout.toMillis(),
                            TimeUnit.MILLISECONDS
                        )
                        TrackedOutboundSession(counterparties, timeStamp(), timeStamp())
                    }
                }
            }
        }

        fun startSendingHeartbeats(session: Session) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was sent before the HeartbeatManager was started.")
                }
                trackedOutboundSessions.computeIfPresent(session.sessionId) { _, trackedSession ->
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
                trackedOutboundSessions.computeIfPresent(session.sessionId) { _, trackedSession ->
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
                val sessionInfo = trackedOutboundSessions[sessionId] ?: return@withLifecycleLock
                logger.trace("Message acknowledged with on a session with Id $sessionId.")
                sessionInfo.lastAckTimestamp = timeStamp()
            }
        }

        fun sessionMessageReceived(sessionId: String) {
            dominoTile.withLifecycleLock {
                check(isRunning) { "A session message was received before the HeartbeatManager was started." }
                messageReceived(sessionId)
            }
        }

        fun dataMessageReceived(sessionId: String) {
            dominoTile.withLifecycleLock {
                check(isRunning) { "A data message was received before the HeartbeatManager was started." }
                messageReceived(sessionId)
            }
        }

        private fun messageReceived(sessionId: String) {
            trackedInboundSessions.compute(sessionId) { _, initialTrackedSession ->
                if (initialTrackedSession != null) {
                    initialTrackedSession.lastReceivedTimestamp = timeStamp()
                    initialTrackedSession
                } else {
                    executorService.schedule(
                        { inboundSessionTimeout(sessionId) },
                        config.get().sessionTimeout.toMillis(),
                        TimeUnit.MILLISECONDS
                    )
                    TrackedInboundSession(timeStamp())
                }
            }
        }

        private fun outboundSessionTimeout(counterparties: SessionCounterparties, sessionId: String) {
            val sessionInfo = trackedOutboundSessions[sessionId] ?: return
            val timeSinceLastAck = timeStamp() - sessionInfo.lastAckTimestamp
            val sessionTimeoutMs = config.get().sessionTimeout.toMillis()
            if (timeSinceLastAck >= sessionTimeoutMs) {
                logger.info(
                    "Outbound session $sessionId (local=${counterparties.ourId}, remote=" +
                            "${counterparties.counterpartyId}) has not received any messages for the configured " +
                            "timeout threshold ($sessionTimeoutMs ms) so it will be cleaned up."
                )
                destroyOutboundSession(counterparties, sessionId)
                trackedOutboundSessions.remove(sessionId)
                recordSessionTimeoutMetric(counterparties.ourId, counterparties.counterpartyId)
            } else {
                executorService.schedule(
                    { outboundSessionTimeout(counterparties, sessionId) },
                    sessionTimeoutMs - timeSinceLastAck,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun inboundSessionTimeout(sessionId: String) {
            val sessionInfo = trackedInboundSessions[sessionId] ?: return
            val timeSinceLastReceived = timeStamp() - sessionInfo.lastReceivedTimestamp
            val sessionTimeoutMs = config.get().sessionTimeout.toMillis()
            if (timeSinceLastReceived >= sessionTimeoutMs) {
                logger.info(
                    "Inbound session $sessionId has not received any messages for the configured timeout " +
                            "threshold ($sessionTimeoutMs ms) so it will be cleaned up."
                )
                destroyInboundSession(sessionId)
                trackedInboundSessions.remove(sessionId)
            } else {
                executorService.schedule(
                    { inboundSessionTimeout(sessionId) },
                    sessionTimeoutMs - timeSinceLastReceived,
                    TimeUnit.MILLISECONDS
                )
            }
        }

        private fun sendHeartbeat(counterparties: SessionCounterparties, session: Session) {
            val sessionInfo = trackedOutboundSessions[session.sessionId]
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
                    counterparties.status,
                    counterparties.serial
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

        private fun sendHeartbeatMessage(
            source: HoldingIdentity,
            dest: HoldingIdentity,
            session: Session,
            filter: MembershipStatusFilter,
            serial: Long
        ) {
            val heartbeatMessage = HeartbeatMessage()
            val message = MessageConverter.linkOutMessageFromHeartbeat(
                source,
                dest,
                heartbeatMessage,
                session,
                groupPolicyProvider,
                membershipGroupReaderProvider,
                filter,
                serial
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

        private fun recordSessionTimeoutMetric(source: HoldingIdentity, destination: HoldingIdentity) {
            CordaMetrics.Metric.OutboundSessionTimeoutCount.builder()
                .withTag(CordaMetrics.Tag.SourceVirtualNode, source.x500Name.toString())
                .withTag(CordaMetrics.Tag.DestinationVirtualNode, destination.x500Name.toString())
                .withTag(CordaMetrics.Tag.MembershipGroup, source.groupId)
                .build().increment()
        }
    }
}
