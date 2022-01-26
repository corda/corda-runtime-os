package net.corda.crypto.persistence.messaging

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

class NewConfigurationReceivedEvent(
    val config: SmartConfig
) : LifecycleEvent