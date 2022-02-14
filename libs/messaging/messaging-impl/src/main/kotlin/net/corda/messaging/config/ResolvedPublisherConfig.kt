package net.corda.messaging.config

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.schema.configuration.MessagingKeys.Bus.BUS
import net.corda.schema.configuration.MessagingKeys.Publisher.CLOSE_TIMEOUT
import java.time.Duration

data class ResolvedPublisherConfig(
    val clientId: String,
    val instanceId: Int?,
    val closeTimeout: Duration,
    val busConfig: SmartConfig
) {
    companion object {
        fun merge(publisherConfig: PublisherConfig, messagingConfig: SmartConfig): ResolvedPublisherConfig {
            return ResolvedPublisherConfig(
                publisherConfig.clientId,
                publisherConfig.instanceId,
                Duration.ofMillis(messagingConfig.getLong(CLOSE_TIMEOUT)),
                messagingConfig.getConfig(BUS)
            )
        }
    }
}
