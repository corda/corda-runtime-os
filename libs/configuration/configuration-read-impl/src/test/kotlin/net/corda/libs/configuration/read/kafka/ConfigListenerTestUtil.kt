package net.corda.libs.configuration.read.kafka

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.read.ConfigListener

class ConfigListenerTestUtil : ConfigListener {
    var update = false
    var lastSnapshot = mapOf<String, SmartConfig>()

    override fun onUpdate(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig>) {
        update = true
        lastSnapshot = currentConfigurationSnapshot
    }
}
