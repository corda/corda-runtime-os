package net.corda.libs.configuration.read

import net.corda.lifecycle.LifeCycle


interface ConfigReadService : LifeCycle {

    /**
     * Starts the service
     * Register your callback before calling this!!!
     */
    override fun start()

    /**
     * Register a callback for any configuration changes
     */
    fun registerCallback(configListener: ConfigListener)
}
