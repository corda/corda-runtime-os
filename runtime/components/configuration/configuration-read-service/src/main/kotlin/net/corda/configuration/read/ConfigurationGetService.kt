package net.corda.configuration.read

import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig

/**
 * Configuration query service.
 *
 * Used to view "active" configuration.
 */
interface ConfigurationGetService {
    /**
     * Get [Configuration] for a given [section], return null if section does not exist
     *
     * @param section
     */
    fun get(section: String): Configuration?

    /**
     * Get [SmartConfig] for a given [section], return null if section does not exist
     *
     * @param section
     */
    fun getSmartConfig(section: String): SmartConfig?
}