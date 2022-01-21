package net.corda.libs.virtualnode.write

import net.corda.libs.configuration.SmartConfig
import javax.persistence.EntityManagerFactory

/** A factory for [VirtualNodeWriter]s. */
interface VirtualNodeWriterFactory {
    /**
     * Creates a [VirtualNodeWriter].
     *
     * @param config Config to use for subscribing to Kafka.
     * @param instanceId The instance ID to use for subscribing to Kafka.
     *
     * @throws VirtualNodeWriterException If the required Kafka publishers and subscriptions cannot be set up.
     */
    fun create(config: SmartConfig, instanceId: Int): VirtualNodeWriter
}