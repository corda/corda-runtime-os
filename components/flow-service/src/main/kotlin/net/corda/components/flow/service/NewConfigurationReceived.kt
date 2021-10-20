package net.corda.components.flow.service

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleEvent

data class NewConfigurationReceived(val configs: Map<String, Config>) : LifecycleEvent
