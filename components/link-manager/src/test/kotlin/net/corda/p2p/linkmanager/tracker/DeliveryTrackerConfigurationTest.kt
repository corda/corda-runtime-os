package net.corda.p2p.linkmanager.tracker

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_MAX_CACHE_OFFSET_AGE
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_MAX_CACHE_SIZE_MEGABYTES
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_MAX_NUMBER_OF_PERSISTENCE_RETRIES
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_OUTBOUND_BATCH_PROCESSING_TIMEOUT_SECONDS
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.DELIVERY_TRACKER_STATE_PERSISTENCE_PERIOD_SECONDS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class DeliveryTrackerConfigurationTest {
    private val configurationTile = DeliveryTrackerConfiguration(
        mock(),
        mock(),
    )

    @Test
    fun `without any notification, default configuration is returned`() {
        val config = configurationTile.config

        assertThat(config).isEqualTo(
            DeliveryTrackerConfiguration.Configuration(
                maxCacheSizeMegabytes = 100,
                maxCacheOffsetAge = 50000,
                statePersistencePeriodSeconds = 1.0,
                outboundBatchProcessingTimeoutSeconds = 30.0,
                maxNumberOfPersistenceRetries = 3,
            ),
        )
    }

    @Test
    fun `applyNewConfiguration will change the configuration`() {
        val config = DeliveryTrackerConfiguration.Configuration(
            maxCacheSizeMegabytes = 200,
            maxCacheOffsetAge = 300,
            statePersistencePeriodSeconds = 400.0,
            outboundBatchProcessingTimeoutSeconds = 500.0,
            maxNumberOfPersistenceRetries = 12,
        )

        configurationTile.applyNewConfiguration(
            config,
            null,
            mock(),
        )

        assertThat(configurationTile.config).isEqualTo(config)
    }

    @Test
    fun `applyNewConfiguration will complete the future`() {
        val config = DeliveryTrackerConfiguration.Configuration(
            maxCacheSizeMegabytes = 200,
            maxCacheOffsetAge = 300,
            statePersistencePeriodSeconds = 400.0,
            outboundBatchProcessingTimeoutSeconds = 500.0,
            maxNumberOfPersistenceRetries = 12,
        )

        val future = configurationTile.applyNewConfiguration(
            config,
            null,
            mock(),
        )

        assertThat(future).isCompleted
    }

    @Test
    fun `applyNewConfiguration will call the listener`() {
        val listener = mock<DeliveryTrackerConfiguration.ConfigurationChanged>()
        configurationTile.lister(listener)
        val config = DeliveryTrackerConfiguration.Configuration(
            maxCacheSizeMegabytes = 200,
            maxCacheOffsetAge = 300,
            statePersistencePeriodSeconds = 400.0,
            outboundBatchProcessingTimeoutSeconds = 500.0,
            maxNumberOfPersistenceRetries = 12,
        )
        configurationTile.applyNewConfiguration(
            config,
            null,
            mock(),
        )

        verify(listener).changed()
    }

    @Test
    fun `applyNewConfiguration will not call the listener if the config has not changed`() {
        val listener = mock<DeliveryTrackerConfiguration.ConfigurationChanged>()
        configurationTile.lister(listener)
        val config = configurationTile.config
        configurationTile.applyNewConfiguration(
            config,
            config,
            mock(),
        )

        verify(listener, never()).changed()
    }

    @Test
    fun `fromConfig returns the correct configuration`() {
        val config = ConfigFactory.empty()
            .withValue(
                DELIVERY_TRACKER_MAX_CACHE_SIZE_MEGABYTES,
                ConfigValueFactory.fromAnyRef(101),
            )
            .withValue(
                DELIVERY_TRACKER_MAX_CACHE_OFFSET_AGE,
                ConfigValueFactory.fromAnyRef(202),
            )
            .withValue(
                DELIVERY_TRACKER_STATE_PERSISTENCE_PERIOD_SECONDS,
                ConfigValueFactory.fromAnyRef(303),
            )
            .withValue(
                DELIVERY_TRACKER_OUTBOUND_BATCH_PROCESSING_TIMEOUT_SECONDS,
                ConfigValueFactory.fromAnyRef(404),
            )
            .withValue(
                DELIVERY_TRACKER_MAX_NUMBER_OF_PERSISTENCE_RETRIES,
                ConfigValueFactory.fromAnyRef(31),
            )

        val configuration = configurationTile.configFactory(config)

        assertThat(configuration).isEqualTo(
            DeliveryTrackerConfiguration.Configuration(
                maxCacheSizeMegabytes = 101,
                maxCacheOffsetAge = 202,
                statePersistencePeriodSeconds = 303.0,
                outboundBatchProcessingTimeoutSeconds = 404.0,
                maxNumberOfPersistenceRetries = 31,
            ),
        )
    }
}
