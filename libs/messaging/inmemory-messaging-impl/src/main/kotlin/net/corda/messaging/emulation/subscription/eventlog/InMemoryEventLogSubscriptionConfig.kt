package net.corda.messaging.emulation.subscription.eventlog

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.properties.InMemProperties

/**
 * Configuration for the in memory event log subscription.
 *
 * @property subscriptionConfig - The subscription configuration
 * @property nodeConfig - Any specific node configuration (to overwrite the default partition size and poll size).
 */
class InMemoryEventLogSubscriptionConfig(
    internal val subscriptionConfig: SubscriptionConfig,
    private val nodeConfig: Config
) {
    internal val partitionSize by lazy {
        nodeConfig.getInt(
            InMemProperties.PARTITION_SIZE
        )
    }
    internal val pollSize by lazy {
        nodeConfig.getInt(
            InMemProperties.TOPICS_POLL_SIZE
        )
    }
}
