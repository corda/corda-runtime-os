package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

// TODO - Joel - Describe.
internal class SubscribeEvent(val config: SmartConfig, val instanceId: Int, val dbUtils: DBUtils) : LifecycleEvent