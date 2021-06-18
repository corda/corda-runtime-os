package net.corda.libs.configuration.read

import net.corda.lifecycle.LifeCycle
import java.util.*


interface ConfigReadService : LifeCycle {
    /**
     * Register a callback for any configuration changes
     * If the service is already running, you will receive a snapshot of all available configurations
     */
    fun registerCallback(configListener: ConfigListener): UUID

    /**
     * Unregister your callback from the config service so you no longer receive updates
     */
    fun unregisterCallback(callbackUUID: UUID)
}
