package net.corda.chunking.read.events

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent
import javax.persistence.EntityManagerFactory

internal class StartProcessingEvent(
    val config: SmartConfig,
    val instanceId: Int,
    val entityManagerFactory: EntityManagerFactory
) : LifecycleEvent
