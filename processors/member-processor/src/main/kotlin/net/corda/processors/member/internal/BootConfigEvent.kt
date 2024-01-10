package net.corda.processors.member.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

data class BootConfigEvent(
    val config: SmartConfig
) : LifecycleEvent