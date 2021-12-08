package net.corda.processors.db.internal.config

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

internal class BootstrapConfigEvent(val config: SmartConfig, val instanceId: Int) : LifecycleEvent
internal class SubscribeEvent : LifecycleEvent