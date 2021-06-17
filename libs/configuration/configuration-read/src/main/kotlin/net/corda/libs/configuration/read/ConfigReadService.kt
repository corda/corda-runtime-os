package net.corda.libs.configuration.read

import com.typesafe.config.Config
import net.corda.lifecycle.LifeCycle


interface ConfigReadService : LifeCycle {

    /**
     * Register a callback for any configuration changes
     * If the service is already running, you will receive a snapshot of all available configurations
     */
    fun registerCallback(configListener: ConfigListener): Map<String, Config>?
}
