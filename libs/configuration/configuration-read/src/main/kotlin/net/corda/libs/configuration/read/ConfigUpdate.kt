package net.corda.libs.configuration.read

import com.typesafe.config.Config

interface ConfigUpdate {

    fun onUpdate(updatedConfig: Map<String, Config>)
}