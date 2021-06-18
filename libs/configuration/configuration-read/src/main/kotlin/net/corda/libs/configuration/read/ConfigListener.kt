package net.corda.libs.configuration.read

import com.typesafe.config.Config

interface ConfigListener {
    fun onUpdate(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config>)
}
