package net.corda.configuration.read

import net.corda.data.config.Configuration
import net.corda.lifecycle.Lifecycle

/**
 * Configuration query service.
 *
 * Used to view "active" configuration.
 */
interface ConfigurationGetService : Lifecycle {
    /**
     * Get [Configuration] for a given [section], return null if section does not exist
     *
     * @param section
     */
    fun get(section: String): Configuration?
}