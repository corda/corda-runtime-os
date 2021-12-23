package net.corda.configuration.publish

import net.corda.libs.configuration.SmartConfig

/** Publishes configuration to Kafka, without persisting it to the cluster database. */
@Suppress("Unused")
interface ConfigPublishService {
    /** Publishes the updated configuration to Kafka. */
    fun updateConfig(destination: String, appConfig: SmartConfig, configurationFile: String)
}