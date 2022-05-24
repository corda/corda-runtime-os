package net.corda.messaging.config

import java.time.Duration
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Publisher.CLOSE_TIMEOUT

/**
 * Class to resolve publisher configuration for the messaging layer.
 */
internal data class ResolvedPublisherConfig(
    val clientId: String,
    val instanceId: Int,
    val transactional: Boolean,
    val closeTimeout: Duration,
    val messageBusConfig: SmartConfig
) {
    companion object {
        /**
         * Merge the user configured values in [publisherConfig] with the [messagingConfig] and return a concrete class containing all
         * values used by a publisher.
         * @param publisherConfig User configurable values for a publisher.
         * @param messagingConfig Messaging smart config.
         * @return Concrete class containing all config values used by a publisher.
         */
        fun merge(publisherConfig: PublisherConfig, messagingConfig: SmartConfig): ResolvedPublisherConfig {
            return ResolvedPublisherConfig(
                publisherConfig.clientId,
                messagingConfig.getInt(INSTANCE_ID),
                publisherConfig.transactional,
                Duration.ofMillis(messagingConfig.getLong(CLOSE_TIMEOUT)),
                messagingConfig
            )
        }
    }

    val loggerName = "PUBLISHER-$clientId"
}
