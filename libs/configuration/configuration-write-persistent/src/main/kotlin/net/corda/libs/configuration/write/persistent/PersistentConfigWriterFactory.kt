package net.corda.libs.configuration.write.persistent

import net.corda.libs.configuration.SmartConfig

/** A factory for [PersistentConfigWriter]s. */
interface PersistentConfigWriterFactory {
    /**
     * Creates a [PersistentConfigWriter].
     *
     * @param config Config to be used by the subscription.
     * @param instanceId The instance ID to use for subscribing to Kafka.
     *
     * TODO - Joel - Check what this throws and document it.
     */
    fun create(config: SmartConfig, instanceId: Int): PersistentConfigWriter
}