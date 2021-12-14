package net.corda.configuration.write.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/** The [config] and [instanceId] for connecting to Kafka. */
internal class SubscribeEvent(val config: SmartConfig, val instanceId: Int) : LifecycleEvent