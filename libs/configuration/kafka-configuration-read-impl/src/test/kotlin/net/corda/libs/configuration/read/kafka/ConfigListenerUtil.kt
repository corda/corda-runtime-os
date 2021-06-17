package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener

class ConfigListenerUtil : ConfigListener {

    var update = false
    override fun onSnapshot(currentConfigurationSnapshot: Map<String, Config>) {
        update = true
    }

    override fun onUpdate(changedKey: String, updatedConfig: Map<String, Config>) {
        update = true
    }
}