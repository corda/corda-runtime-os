package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException

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
     * @throws CordaMessageAPIFatalException For non-recoverable errors setting up the subscription.
     * @throws CordaMessageAPIIntermittentException For recoverable errors setting up the subscription.
     */
    fun create(config: SmartConfig, instanceId: Int): Lifecycle
}