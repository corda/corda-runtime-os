package net.corda.configuration.read

import net.corda.data.config.Configuration

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
}