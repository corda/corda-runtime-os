package net.corda.libs.configuration.read

import com.typesafe.config.Config

interface ConfigListener {
    fun onSnapshot(currentConfigurationSnapshot: Map<String, Config>)

    fun onUpdate(changedKey: String, currentConfigurationSnapshot: Map<String, Config>)
}
