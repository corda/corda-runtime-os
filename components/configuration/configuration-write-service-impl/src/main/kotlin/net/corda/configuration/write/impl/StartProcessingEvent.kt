package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent
import javax.persistence.EntityManagerFactory

/**
 * Indicates that the [ConfigWriteService] should start processing cluster configuration updates.
 *
 * @param config Config to be used by the subscription.
 * @param instanceId The instance ID to use for subscribing to Kafka.
 * @param entityManagerFactory The factory for creating entity managers for interacting with the cluster database.
 */
internal class StartProcessingEvent(
    val config: SmartConfig,
    val instanceId: Int,
    val entityManagerFactory: EntityManagerFactory
) : LifecycleEvent