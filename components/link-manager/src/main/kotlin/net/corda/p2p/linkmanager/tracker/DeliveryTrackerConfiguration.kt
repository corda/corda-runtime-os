package net.corda.p2p.linkmanager.tracker

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_MAX_CACHE_OFFSET_AGE
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_MAX_CACHE_SIZE_MEGABYTES
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_MAX_NUMBER_OF_PERSISTENCE_RETRIES
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_OUTBOUND_BATCH_PROCESSING_TIMEOUT_SECONDS
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_STATE_PERSISTENCE_PERIOD_SECONDS
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.schema.configuration.ConfigKeys
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal class DeliveryTrackerConfiguration(
    configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
) : LifecycleWithDominoTile,
    ConfigurationChangeHandler<DeliveryTrackerConfiguration.Configuration>(
        configurationReaderService,
        ConfigKeys.P2P_LINK_MANAGER_CONFIG,
        ::fromConfig,
    ) {
    private companion object {
        const val NAME = "DeliveryTrackerConfiguration"
        fun fromConfig(config: Config): Configuration {
            return Configuration(
                maxCacheSizeMegabytes = config.getLong(DELIVERY_TRACKER_MAX_CACHE_SIZE_MEGABYTES),
                maxCacheOffsetAge = config.getLong(DELIVERY_TRACKER_MAX_CACHE_OFFSET_AGE),
                statePersistencePeriodSeconds = config.getDouble(DELIVERY_TRACKER_STATE_PERSISTENCE_PERIOD_SECONDS),
                outboundBatchProcessingTimeoutSeconds = config.getDouble(DELIVERY_TRACKER_OUTBOUND_BATCH_PROCESSING_TIMEOUT_SECONDS),
                maxNumberOfPersistenceRetries = config.getInt(DELIVERY_TRACKER_MAX_NUMBER_OF_PERSISTENCE_RETRIES),
            )
        }
    }
    data class Configuration(
        val maxCacheSizeMegabytes: Long,
        val maxCacheOffsetAge: Long,
        val maxNumberOfPersistenceRetries: Int,
        val statePersistencePeriodSeconds: Double,
        val outboundBatchProcessingTimeoutSeconds: Double,
    )
    interface ConfigurationChanged {
        fun changed()
    }

    private val configuration = AtomicReference(
        Configuration(
            maxCacheSizeMegabytes = 100,
            maxCacheOffsetAge = 50000,
            statePersistencePeriodSeconds = 1.0,
            outboundBatchProcessingTimeoutSeconds = 30.0,
            maxNumberOfPersistenceRetries = 3,
        ),
    )
    private val listeners = ConcurrentHashMap.newKeySet<ConfigurationChanged>()

    val config: Configuration
        get() = configuration.get()

    fun lister(listener: ConfigurationChanged) {
        listeners.add(listener)
    }

    override val dominoTile = ComplexDominoTile(
        componentName = NAME,
        coordinatorFactory = coordinatorFactory,
        configurationChangeHandler = this,
    )

    override fun applyNewConfiguration(
        newConfiguration: Configuration,
        oldConfiguration: Configuration?,
        resources: ResourcesHolder,
    ): CompletableFuture<Unit> {
        if (newConfiguration != oldConfiguration) {
            configuration.set(newConfiguration)
            listeners.forEach {
                it.changed()
            }
        }
        return CompletableFuture.completedFuture(Unit)
    }
}
