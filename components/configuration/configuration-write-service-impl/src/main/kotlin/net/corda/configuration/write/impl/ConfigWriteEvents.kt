package net.corda.configuration.write.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

internal data class BootstrapConfigEvent(val bootConfig: SmartConfig) : LifecycleEvent