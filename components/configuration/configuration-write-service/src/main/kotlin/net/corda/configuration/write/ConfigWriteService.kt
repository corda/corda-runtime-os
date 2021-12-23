package net.corda.configuration.write

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/** Receives configuration updates via RPC, persists them to the cluster database, and publishes them to Kafka. */
interface ConfigWriteService : Lifecycle {

    /**
     * Starts processing cluster configuration updates.
     *
     * @param config Config to be used by the subscription.
     * @param instanceId The instance ID to use for subscribing to Kafka.
     */
    fun startProcessing(config: SmartConfig, instanceId: Int)
}