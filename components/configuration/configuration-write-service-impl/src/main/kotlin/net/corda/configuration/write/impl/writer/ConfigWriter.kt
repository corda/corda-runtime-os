package net.corda.configuration.write.impl.writer

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.Publisher

/**
 * Upon [start], listens for configuration management requests using an
 * `RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>`. Persists the updated
 * configuration to the cluster database and publishes the updated configuration to Kafka.
 *
 * Upon [stop], stops listening for configuration management requests and publishing updated configuration.
 */
internal class ConfigWriter internal constructor(
    private val subscription: ConfigurationManagementRPCSubscription,
    private val publisher: Publisher
) : Lifecycle {

    override val isRunning get() = subscription.isRunning

    override fun start() {
        subscription.start()
        publisher.start()
    }

    override fun stop() {
        subscription.stop()
        publisher.close()
    }
}