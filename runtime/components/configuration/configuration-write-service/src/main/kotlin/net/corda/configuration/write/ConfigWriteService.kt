package net.corda.configuration.write

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/**
 * Receives configuration updates via RPC, persists them to the cluster database, and publishes them to Kafka.
 *
 * [ConfigWriteService] needs boot config to be started and it is needed for config read service to start working.
 * Upon receiving boot config, it will start processing cluster configuration updates.
 */
interface ConfigWriteService : Lifecycle {

    /**
     * Provides boot configuration to the configuration write service.
     *
     * @param bootConfig Config to be used by the subscription.
     * @param entityManagerFactory The factory for creating entity managers for interacting with the cluster database.
     */
    fun bootstrapConfig(bootConfig: SmartConfig)
}