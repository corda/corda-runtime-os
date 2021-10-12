package net.corda.libs.configuration.read

import net.corda.lifecycle.Lifecycle


interface ConfigReader : Lifecycle {
    /**
     * Register a callback for any configuration changes
     * If the service is already running, you will receive a snapshot of all available configurations
     */
    fun registerCallback(configListener: ConfigListener): AutoCloseable
}
