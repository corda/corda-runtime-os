package net.corda.libs.configuration.read

import com.typesafe.config.Config

interface ConfigReadService {

    /**
     * Start the service
     */
    fun start()

    /**
     * Retrieve the configuration associated with the given
     * @param componentName
     */
    fun getConfiguration(componentName: String): Config

    /**
     * Retrieve all available configurations
     */
    fun getAllConfiguration(): Map<String,Config>

    /**
     * Retrieve the configuration associated with the given [componentName] and parse it to the given [clazz]
     * @param componentName
     * @param clazz The class you want the retrieved information to be cast as
     */
    fun <T> parseConfiguration(componentName: String, clazz: Class<T>): T

    /**
     * Register a callback for any configuration changes
     */
    fun registerCallback(configUpdate: ConfigUpdate)
}
