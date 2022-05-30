package net.corda.configuration.write

import javax.persistence.EntityManagerFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/** Receives configuration updates via RPC, persists them to the cluster database, and publishes them to Kafka. */
interface ConfigWriteService : Lifecycle {

    /**
     * Starts processing cluster configuration updates.
     *
     * @param bootConfig Config to be used by the subscription.
     * @param entityManagerFactory The factory for creating entity managers for interacting with the cluster database.
     */
    fun startProcessing(bootConfig: SmartConfig, entityManagerFactory: EntityManagerFactory)
}