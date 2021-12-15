package net.corda.libs.configuration.write.persistent

import net.corda.lifecycle.Lifecycle

/**
 * Upon [start], listens for configuration management requests on [TOPIC_CONFIG_MGMT_REQUEST] using an
 * `RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>`. Persists the updated
 * configuration to the cluster database and publishes the updated configuration to [TOPIC_CONFIG].
 *
 * Upon [stop], stops listening for configuration management requests and publishing updated configuration.
 */
interface PersistentConfigWriter : Lifecycle