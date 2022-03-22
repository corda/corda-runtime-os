package net.corda.messaging.config

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Publisher.CLOSE_TIMEOUT
import java.time.Duration

internal data class ResolvedPublisherConfig(
    val clientId: String,
    val instanceId: Int?,
    val closeTimeout: Duration,
    val busConfig: SmartConfig
) {
    companion object {
        fun merge(publisherConfig: PublisherConfig, messagingConfig: SmartConfig): ResolvedPublisherConfig {
            return ResolvedPublisherConfig(
                publisherConfig.clientId,
                if (messagingConfig.hasPath(INSTANCE_ID)) messagingConfig.getInt(INSTANCE_ID) else null,
                Duration.ofMillis(messagingConfig.getLong(CLOSE_TIMEOUT)),
                messagingConfig
            )
        }
    }

    val loggerName = "PUBLISHER-$clientId"
}
