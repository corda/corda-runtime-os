package net.corda.libs.configuration.write

import net.corda.lifecycle.Lifecycle

/**
 * Upon [start], listens for configuration management requests using an
 * `RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>`. Persists the updated
 * configuration to the cluster database and publishes the updated configuration to Kafka.
 *
 * Upon [stop], stops listening for configuration management requests and publishing updated configuration.
 */
interface ConfigWriter : Lifecycle