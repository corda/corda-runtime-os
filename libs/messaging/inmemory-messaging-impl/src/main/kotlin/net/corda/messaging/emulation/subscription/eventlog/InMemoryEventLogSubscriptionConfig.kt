package net.corda.messaging.emulation.subscription.eventlog

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.properties.InMemProperties
import net.corda.messaging.emulation.properties.getIntOrDefault

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
        nodeConfig.getIntOrDefault(
            InMemProperties.PARTITION_SIZE,
            InMemProperties.DEFAULT_PARTITION_SIZE
        )
    }
    internal val pollSize by lazy {
        nodeConfig.getIntOrDefault(
            InMemProperties.TOPICS_POLL_SIZE,
            InMemProperties.DEFAULT_POLL_SIZE
        )
    }
}
