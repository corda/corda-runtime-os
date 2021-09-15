package net.corda.flow.worker

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleEvent

data class NewConfigurationReceived(val config: Config) : LifecycleEvent