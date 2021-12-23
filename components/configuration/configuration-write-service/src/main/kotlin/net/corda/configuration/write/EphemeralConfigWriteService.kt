package net.corda.configuration.write

import net.corda.libs.configuration.SmartConfig

/** Publishes configuration updates to Kafka, without persisting them to the cluster database. */
interface EphemeralConfigWriteService {
    // TODO - Joel - What is `destination`? Document the params.
    /** Updates the configuration published on Kafka. */
    fun updateConfig(destination: String, appConfig: SmartConfig, configurationFile: String)
}