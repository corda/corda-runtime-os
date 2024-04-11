package net.corda.p2p.linkmanager.common

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.forwarding.gateway.TlsCertificatesPublisher
import net.corda.p2p.linkmanager.forwarding.gateway.TrustStoresPublisher
import net.corda.p2p.linkmanager.forwarding.gateway.mtls.ClientCertificatePublisher
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.sessions.DeadSessionMonitor
import net.corda.p2p.linkmanager.sessions.DeadSessionMonitorConfigurationHandler
import net.corda.p2p.linkmanager.sessions.PendingSessionMessageQueuesImpl
import net.corda.p2p.linkmanager.sessions.ReEstablishmentMessageSender
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.StateConvertor
import net.corda.p2p.linkmanager.sessions.StateManagerWrapper
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventPublisher
import net.corda.p2p.linkmanager.sessions.expiration.SessionExpirationScheduler
import net.corda.p2p.linkmanager.sessions.expiration.StaleSessionProcessor
import net.corda.p2p.linkmanager.sessions.lookup.SessionLookupImpl
import net.corda.p2p.linkmanager.sessions.messages.SessionMessageProcessor
import net.corda.p2p.linkmanager.sessions.writer.SessionWriterImpl
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.concurrent.Executors

@Suppress("LongParameterList")
internal class CommonComponents(
    internal val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    linkManagerHostingMap: LinkManagerHostingMap,
    groupPolicyProvider: GroupPolicyProvider,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    internal val configurationReaderService: ConfigurationReadService,
    cryptoOpsClient: CryptoOpsClient,
    internal val subscriptionFactory: SubscriptionFactory,
    internal val publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    cpiInfoReadService: CpiInfoReadService,
    membershipQueryClient: MembershipQueryClient,
    groupParametersReaderService: GroupParametersReaderService,
    internal val clock: Clock,
    internal val stateManager: StateManager,
    schemaRegistry: AvroSchemaRegistry,
    sessionEncryptionOpsClient: SessionEncryptionOpsClient,
) : LifecycleWithDominoTile {
    private companion object {
        const val LISTENER_NAME = "link.manager.group.policy.listener"
    }

    internal val messageConverter = MessageConverter(
        groupPolicyProvider,
        membershipGroupReaderProvider,
        clock,
    )

    internal val messagesPendingSession = PendingSessionMessageQueuesImpl(
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        messageConverter,
    )

    private val sessionEventPublisher = StatefulSessionEventPublisher(
        lifecycleCoordinatorFactory,
        publisherFactory,
        messagingConfiguration,
    )

    // no lifecycle
    // OK
    private val sessionCache = SessionCache(
        stateManager,
        sessionEventPublisher,
    )

    // no lifecycle
    // OK
    private val sessionExpirationScheduler = SessionExpirationScheduler(
        clock,
        sessionCache,
    )

    private val staleSessionProcessor = StaleSessionProcessor(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        messagingConfiguration,
        clock,
        stateManager,
        sessionCache,
    )

    private val deadSessionMonitor = DeadSessionMonitor(
        Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "Dead Session Monitor") },
        sessionCache,
    )

    private val deadSessionMonitorConfigHandler =
        DeadSessionMonitorConfigurationHandler(deadSessionMonitor, configurationReaderService)

    private val p2pRecordsFactory = P2pRecordsFactory(clock, cordaAvroSerializationFactory)

    // no lifecycle
    // OK
    private val sessionWriter = SessionWriterImpl(
        sessionCache,
    )

    // no lifecycle
    private val stateConvertor = StateConvertor(
        schemaRegistry,
        sessionEncryptionOpsClient,
    )

    // existing lifecycle, nothing needs to be added
    // OK
    private val oldSessionManager = SessionManagerImpl(
        groupPolicyProvider,
        membershipGroupReaderProvider,
        cryptoOpsClient,
        messagesPendingSession,
        publisherFactory,
        configurationReaderService,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        linkManagerHostingMap,
        sessionCache = sessionCache,
    )

    // no lifecycle
    // OK
    private val reEstablishmentMessageSender = ReEstablishmentMessageSender(
        p2pRecordsFactory,
        oldSessionManager,
    )

    // existing lifecycle, nothing needs to be added
    // OK
    /*val sessionEventListener = StatefulSessionEventProcessor(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        messagingConfiguration,
        stateManager,
        stateConvertor,
        sessionCache,
        oldSessionManager,
    )*/

    // no lifecycle
    // OK
    private val stateManagerWrapper = StateManagerWrapper(
        stateManager,
        sessionCache,
        sessionExpirationScheduler,
        stateConvertor,
        oldSessionManager.revocationCheckerClient::checkRevocation,
        reEstablishmentMessageSender,
    )

    // has lifecycle
    private val sessionLookup = SessionLookupImpl(
        lifecycleCoordinatorFactory,
        sessionCache,
        sessionWriter,
        membershipGroupReaderProvider,
        stateManagerWrapper,
    )

    // has lifecycle
    private val sessionMessageProcessor = SessionMessageProcessor(
        lifecycleCoordinatorFactory,
        clock,
        stateManagerWrapper,
        oldSessionManager,
        stateConvertor,
    )

    internal val sessionManager = StatefulSessionManagerImpl(
        subscriptionFactory,
        messagingConfiguration,
        lifecycleCoordinatorFactory,
        stateManager,
        stateManagerWrapper,
        oldSessionManager,
        stateConvertor,
        clock,
        membershipGroupReaderProvider,
        deadSessionMonitor,
        schemaRegistry,
        sessionCache,
        sessionLookup,
        sessionWriter,
        sessionMessageProcessor,
        sessionEventPublisher = sessionEventPublisher,
    )

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
    ).also {
        groupPolicyProvider.registerListener(LISTENER_NAME) { holdingIdentity, groupPolicy ->
            it.groupAdded(holdingIdentity, groupPolicy)
        }
    }

    private val tlsCertificatesPublisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
    ).also {
        linkManagerHostingMap.registerListener(it)
    }

    private val mtlsClientCertificatePublisher = ClientCertificatePublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        groupPolicyProvider,
    )

    private val externalDependencies = listOf(
        NamedLifecycle.of(groupPolicyProvider),
        NamedLifecycle.of(membershipGroupReaderProvider),
        NamedLifecycle.of(cryptoOpsClient),
    )

    private val externalManagedDependencies = listOf(
        NamedLifecycle.of(virtualNodeInfoReadService),
        NamedLifecycle.of(cpiInfoReadService),
        NamedLifecycle.of(membershipQueryClient),
        NamedLifecycle.of(groupParametersReaderService),
        NamedLifecycle.of(sessionEncryptionOpsClient),
    ) + externalDependencies

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            linkManagerHostingMap.dominoTile.coordinatorName,
            messagesPendingSession.dominoTile.coordinatorName,
            sessionManager.dominoTile.coordinatorName,
            trustStoresPublisher.dominoTile.coordinatorName,
            tlsCertificatesPublisher.dominoTile.coordinatorName,
            mtlsClientCertificatePublisher.dominoTile.coordinatorName,
        ) + externalDependencies.map {
            it.name
        } + stateManager.name,
        managedChildren = listOf(
            linkManagerHostingMap.dominoTile.toNamedLifecycle(),
            messagesPendingSession.dominoTile.toNamedLifecycle(),
            sessionManager.dominoTile.toNamedLifecycle(),
            trustStoresPublisher.dominoTile.toNamedLifecycle(),
            tlsCertificatesPublisher.dominoTile.toNamedLifecycle(),
            mtlsClientCertificatePublisher.dominoTile.toNamedLifecycle(),
            staleSessionProcessor.dominoTile.toNamedLifecycle(),
        ) + externalManagedDependencies,
        configurationChangeHandler = deadSessionMonitorConfigHandler,
        onClose = { sessionCache.close() }
    )
}
