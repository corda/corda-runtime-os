package net.corda.configuration.write.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/** The [config] and [instanceId] for connecting to Kafka, plus [dbUtils] for interacting with the database. */
internal class SubscribeEvent(val config: SmartConfig, val instanceId: Int, val dbUtils: DBUtils) : LifecycleEvent