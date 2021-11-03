package net.corda.components.flow.service

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

data class NewConfigurationReceived(val configs: Map<String, SmartConfig>) : LifecycleEvent
