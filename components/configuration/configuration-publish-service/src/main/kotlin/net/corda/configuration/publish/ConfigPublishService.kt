package net.corda.configuration.publish

import net.corda.libs.configuration.SmartConfig

/** Publishes configuration to Kafka, without persisting it to the cluster database. */
interface ConfigPublishService {
    /**
     * Publishes the updated configuration to Kafka.
     *
     * @param topic The Kafka topic to publish the configuration to.
     * @param appConfig The bootstrap config required for connecting to Kafka.
     * @param configuration The configuration in JSON or HOCON format.
     */
    fun updateConfig(topic: String, appConfig: SmartConfig, configuration: String)
}