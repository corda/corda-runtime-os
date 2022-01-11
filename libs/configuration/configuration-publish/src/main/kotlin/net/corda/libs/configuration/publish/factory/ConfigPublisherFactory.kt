package net.corda.libs.configuration.publish.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.publish.ConfigPublisher

/**
 * Factory for creating instances of [ConfigPublisher]
 */
interface ConfigPublisherFactory {

    /**
     * @return An instance of [ConfigPublisher]
     */
    fun createPublisher(destination: String, config: SmartConfig) : ConfigPublisher
}