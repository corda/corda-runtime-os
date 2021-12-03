package net.corda.virtualnode.common

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/** Custom event that allows us to send around configuration changes */
class ConfigChangedEvent(val config: SmartConfig) : LifecycleEvent
