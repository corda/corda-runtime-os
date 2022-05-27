package net.corda.configuration.publish.impl.handler

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

internal data class BootstrapConfigEvent(val bootConfig: SmartConfig) : LifecycleEvent