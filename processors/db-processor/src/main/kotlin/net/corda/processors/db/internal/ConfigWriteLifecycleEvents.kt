package net.corda.processors.db.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

internal class ConfigProvidedEvent(val config: SmartConfig, val instanceId: Int) : LifecycleEvent
internal class StartPubSubEvent : LifecycleEvent