package net.corda.libs.configuration.publish

import com.typesafe.config.Config

/** Publishes configuration to Kafka, without persisting it to the cluster database. */
interface ConfigPublisher {
    /**
     * When updating, the stored configuration object for the [configKey] will be completely replaced by the given [config] object
     *
     * @param configKey used to uniquely identify the config
     * @param config Changes to be recorded
     */
    fun updateConfiguration(
        configKey: CordaConfigurationKey,
        config: Config
    )
}