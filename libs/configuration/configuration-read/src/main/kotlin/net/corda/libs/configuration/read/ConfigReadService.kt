package net.corda.libs.configuration.read


interface ConfigReadService {

    /**
     * Starts the service
     * Register your callback before calling this!!!
     */
    fun start()

    /**
     * Register a callback for any configuration changes
     */
    fun registerCallback(configListener: ConfigListener)
}
