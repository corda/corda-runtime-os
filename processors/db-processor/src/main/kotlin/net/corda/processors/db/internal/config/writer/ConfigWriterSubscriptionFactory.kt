package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/** Creates subscriptions that process new config requests. */
interface ConfigWriterSubscriptionFactory {

    /**
     * Creates and starts a subscription that processes new config requests.
     *
     * @param config Config to be used by the subscription.
     * @param instanceId The instance ID to use if subscribing to Kafka.
     *
     * @return A handle to the started subscription.
     *
     * // TODO - Joel - Document what can be thrown.
     */
    fun create(config: SmartConfig, instanceId: Int): Lifecycle
}