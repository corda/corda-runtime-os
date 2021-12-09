package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/**
 * Receives RPC requests to update the cluster's config, updates the config in the cluster database, and publishes the
 * updated config for use by the rest of the cluster.
 */
interface ConfigWriteService : Lifecycle {

    /** Bootstraps the [ConfigWriteService] by providing the required information to connect to Kafka. */
    fun bootstrapConfig(config: SmartConfig, instanceId: Int)
}