package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

internal class SubscribeEvent(val config: SmartConfig, val instanceId: Int) : LifecycleEvent