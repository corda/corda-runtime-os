package net.corda.libs.configuration.read


interface ConfigReadService {

    /**
     * Register a callback for any configuration changes
     */
    fun registerCallback(configListener: ConfigListener): ConfigSubscription
}
