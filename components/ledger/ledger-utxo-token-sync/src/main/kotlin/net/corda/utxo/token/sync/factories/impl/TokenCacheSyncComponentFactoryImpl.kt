package net.corda.utxo.token.sync.factories.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.utxo.token.sync.TokenCacheSyncComponentFactory
import net.corda.utxo.token.sync.TokenCacheSyncServiceComponent
import net.corda.utxo.token.sync.converters.impl.DbRecordConverterImpl
import net.corda.utxo.token.sync.converters.impl.EntityConverterImpl
import net.corda.utxo.token.sync.converters.impl.EventConverterImpl
import net.corda.utxo.token.sync.entities.SyncRequest
import net.corda.utxo.token.sync.handlers.SyncRequestHandler
import net.corda.utxo.token.sync.handlers.impl.FullSyncRequestHandler
import net.corda.utxo.token.sync.handlers.impl.UnspentSyncCheckRequestHandler
import net.corda.utxo.token.sync.handlers.impl.WakeUpHandler
import net.corda.utxo.token.sync.services.impl.SyncConfigurationImpl
import net.corda.utxo.token.sync.services.impl.SyncServiceImpl
import net.corda.utxo.token.sync.services.impl.SyncWakeUpSchedulerImpl
import net.corda.utxo.token.sync.services.impl.TokenCacheSyncServiceComponentImpl
import net.corda.utxo.token.sync.services.impl.TokenCacheSyncSubscriptionHandlerImpl
import net.corda.utxo.token.sync.services.impl.UtxoTokenRepositoryImpl
import net.corda.utxo.token.sync.services.impl.WakeUpGeneratorServiceImpl
import net.corda.v5.base.util.uncheckedCast
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.Executors

@Suppress("LongParameterList", "Unused")
@Component(service = [TokenCacheSyncComponentFactory::class])
class TokenCacheSyncComponentFactoryImpl(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val dbConnectionManager: DbConnectionManager,
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val clock: Clock
) : TokenCacheSyncComponentFactory {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
        @Reference(service = DbConnectionManager::class)
        dbConnectionManager: DbConnectionManager,
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
    ) : this(
        coordinatorFactory,
        configurationReadService,
        virtualNodeInfoReadService,
        dbConnectionManager,
        subscriptionFactory,
        publisherFactory,
        UTCClock()
    )

    override fun create(): TokenCacheSyncServiceComponent {

        val syncConfiguration = SyncConfigurationImpl()
        val entityConverter = EntityConverterImpl(clock, syncConfiguration)
        val eventConverter = EventConverterImpl(entityConverter)
        val messagingRecordFactory = MessagingRecordFactoryImpl(entityConverter)
        val dbRecordConverter = DbRecordConverterImpl()
        val repository = UtxoTokenRepositoryImpl(dbRecordConverter)

        val syncWakeUpScheduler = SyncWakeUpSchedulerImpl(
            publisherFactory,
            messagingRecordFactory,
            Executors.newSingleThreadScheduledExecutor(),
            clock
        )

        val wakeUpGeneratorService = WakeUpGeneratorServiceImpl(
            virtualNodeInfoReadService,
            messagingRecordFactory,
            publisherFactory
        )

        val syncService = SyncServiceImpl(
            repository,
            virtualNodeInfoReadService,
            dbConnectionManager,
            syncConfiguration
        )

        val eventHandlerMap = mapOf<Class<*>, SyncRequestHandler<in SyncRequest>>(
            createHandler(WakeUpHandler(syncService, messagingRecordFactory)),
            createHandler(UnspentSyncCheckRequestHandler(syncService, messagingRecordFactory)),
            createHandler(FullSyncRequestHandler(syncConfiguration, syncService, messagingRecordFactory, clock))
        )

        val tokenCacheEventHandlerFactory = TokenCacheSyncRequestProcessorFactoryImpl(
            eventConverter,
            entityConverter,
            eventHandlerMap
        )

        val tokenCacheSubscriptionHandler = TokenCacheSyncSubscriptionHandlerImpl(
            coordinatorFactory,
            subscriptionFactory,
            tokenCacheEventHandlerFactory,
            syncWakeUpScheduler
        ) { cfg -> cfg.getConfig(ConfigKeys.MESSAGING_CONFIG) }

        return TokenCacheSyncServiceComponentImpl(
            coordinatorFactory,
            configurationReadService,
            syncConfiguration,
            wakeUpGeneratorService,
            syncWakeUpScheduler,
            tokenCacheSubscriptionHandler,
        )
    }

    private inline fun <reified T : SyncRequest> createHandler(
        handler: SyncRequestHandler<in T>
    ): Pair<Class<T>, SyncRequestHandler<in SyncRequest>> {
        return Pair(T::class.java, uncheckedCast(handler))
    }
}
