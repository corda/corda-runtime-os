package net.corda.configuration.write.impl.publish

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

internal data class BootstrapConfigEvent(val bootstrapConfig: SmartConfig) : LifecycleEvent