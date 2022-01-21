package net.corda.virtualnode.write.db.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent
import javax.persistence.EntityManagerFactory

/**
 * Indicates that the `VirtualNodeWriteService` should start processing cluster configuration updates.
 *
 * @param config Config to use for subscribing to Kafka.
 * @param instanceId The instance ID to use for subscribing to Kafka.
 * @param entityManagerFactory The factory for creating entity managers for interacting with the cluster database.
 */
internal class StartProcessingEvent(
    val config: SmartConfig,
    val instanceId: Int,
    val entityManagerFactory: EntityManagerFactory
) : LifecycleEvent