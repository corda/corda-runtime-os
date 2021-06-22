package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener

class ConfigListenerTestUtil : ConfigListener {
    var update = false
    var lastSnapshot = mapOf<String,Config>()

    override fun onUpdate(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config>) {
        update = true
        lastSnapshot = currentConfigurationSnapshot
    }
}
