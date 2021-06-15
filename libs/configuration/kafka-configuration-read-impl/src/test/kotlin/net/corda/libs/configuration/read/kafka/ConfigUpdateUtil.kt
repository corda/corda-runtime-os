package net.corda.libs.configuration.read.kafka

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigUpdate

class ConfigUpdateUtil : ConfigUpdate {

    var update = false

    override fun onUpdate(updatedConfig: Map<String, Config>) {
        update = true
    }
}