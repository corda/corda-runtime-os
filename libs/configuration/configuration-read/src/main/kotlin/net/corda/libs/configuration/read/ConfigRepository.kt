package net.corda.libs.configuration.read

import com.typesafe.config.Config


/**
 * In memory configuration storage class
 */
interface ConfigRepository {

    /**
     * Retrieve the in memory copy of all stored configurations
     */
    fun getConfigurations(): Map<String, Config>

    /**
     * Store all [configuration] objects in memory
     */
    fun storeConfiguration(configuration: Map<String, Config>)


    /**
     * Updates configuration [key] with the new [value]
     */
    fun updateConfiguration(key: String, value: Config)
}