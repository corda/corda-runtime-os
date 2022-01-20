package net.corda.crypto.persistence.kafka

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

class NewConfigurationReceivedEvent(
    val config: SmartConfig
) : LifecycleEvent