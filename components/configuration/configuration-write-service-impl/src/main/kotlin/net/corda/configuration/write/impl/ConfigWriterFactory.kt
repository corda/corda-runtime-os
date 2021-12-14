package net.corda.configuration.write.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import java.io.Closeable

/** Creates listeners that process new config requests. */
interface ConfigWriterFactory {
    /**
     * Creates and starts a listener that processes new config requests.
     *
     * @param config Config to be used by the subscription.
     * @param instanceId The instance ID to use if subscribing to Kafka.
     *
     * @return A handle for closing the subscription.
     *
     * @throws CordaMessageAPIFatalException For non-recoverable errors setting up the subscription.
     * @throws CordaMessageAPIIntermittentException For recoverable errors setting up the subscription.
     */
    fun create(config: SmartConfig, instanceId: Int): Closeable
}