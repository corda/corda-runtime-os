package net.corda.flow.service

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

data class NewConfigurationReceived(val config: SmartConfig) : LifecycleEvent
