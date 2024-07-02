package net.corda.p2p.linkmanager.sessions

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventProcessor
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventPublisher
import net.corda.p2p.linkmanager.sessions.expiration.SessionExpirationScheduler
import net.corda.p2p.linkmanager.sessions.expiration.StaleSessionProcessor
import net.corda.p2p.linkmanager.sessions.lookup.SessionLookup
import net.corda.p2p.linkmanager.sessions.messages.SessionMessageProcessor
import net.corda.p2p.linkmanager.sessions.writer.SessionWriter
import net.corda.p2p.messaging.P2pRecordsFactory
import java.util.concurrent.Executors

internal class SessionManagerCommonComponents(
    cryptoOpsClient: CryptoOpsClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    commonComponents: CommonComponents,
    configurationReadService: ConfigurationReadService,
) : LifecycleWithDominoTile {
    internal val sessionEventPublisher = StatefulSessionEventPublisher(commonComponents)

    internal val sessionCache = SessionCache(
        commonComponents.stateManager,
        sessionEventPublisher,
    )

    internal val sessionMessageHelper = SessionMessageHelper(
        commonComponents,
        cryptoOpsClient,
        sessionCache = sessionCache,
    )

    internal val sessionEventListener = StatefulSessionEventProcessor(
        commonComponents,
        configurationReadService,
        sessionCache,
        sessionMessageHelper,
    )

    internal val sessionExpirationScheduler = SessionExpirationScheduler(
        commonComponents.clock,
        sessionCache,
    )

    private val p2pRecordsFactory = P2pRecordsFactory(commonComponents.clock, cordaAvroSerializationFactory)

    internal val reEstablishmentMessageSender = ReEstablishmentMessageSender(
        p2pRecordsFactory,
        sessionMessageHelper,
    )

    internal val stateManagerWrapper = StateManagerWrapper(
        commonComponents.stateManager,
        sessionExpirationScheduler,
    )

    internal val deadSessionMonitor = DeadSessionMonitor(
        Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "Dead Session Monitor") },
        sessionCache,
    )

    private val deadSessionMonitorConfigHandler =
        DeadSessionMonitorConfigurationHandler(deadSessionMonitor, commonComponents.configurationReaderService)

    private val sessionWriter = SessionWriter(
        sessionCache,
    )

    private val sessionLookup = SessionLookup(
        commonComponents,
        sessionCache,
        sessionWriter,
        sessionExpirationScheduler,
        sessionMessageHelper.revocationCheckerClient::checkRevocation,
        reEstablishmentMessageSender,
    )

    private val sessionMessageProcessor = SessionMessageProcessor(
        commonComponents,
        sessionMessageHelper,
        sessionLookup,
    )

    internal val sessionManager = StatefulSessionManagerImpl(
        commonComponents,
        sessionEventListener,
        sessionEventPublisher,
        stateManagerWrapper,
        sessionMessageHelper,
        deadSessionMonitor,
        sessionCache,
        sessionLookup,
        sessionWriter,
        sessionMessageProcessor,
    )

    private val staleSessionProcessor = StaleSessionProcessor(
        commonComponents,
        sessionCache,
    )

    private val externalDependencies = listOf(
        NamedLifecycle.of(cryptoOpsClient),
    )

    private val externalManagedDependencies = externalDependencies

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        commonComponents.lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            commonComponents.dominoTile.coordinatorName,
            sessionManager.dominoTile.coordinatorName,
        ) + externalDependencies.map {
            it.name
        },
        managedChildren = listOf(
            sessionManager.dominoTile.toNamedLifecycle(),
            staleSessionProcessor.dominoTile.toNamedLifecycle(),
        ) + externalManagedDependencies,
        configurationChangeHandler = deadSessionMonitorConfigHandler,
        onClose = { sessionCache.close() }
    )
}