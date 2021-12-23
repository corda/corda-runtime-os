package net.corda.libs.configuration.write

import net.corda.libs.configuration.SmartConfig

/** A factory for [ConfigWriter]s. */
interface ConfigWriterFactory {
    /**
     * Creates a [ConfigWriter].
     *
     * @param config Config to be used by the subscription.
     * @param instanceId The instance ID to use for subscribing to Kafka.
     *
     * @throws ConfigWriterException If the required Kafka publishers and subscriptions cannot be set up.
     */
    fun create(config: SmartConfig, instanceId: Int): ConfigWriter
}