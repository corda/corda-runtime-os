package net.corda.libs.configuration.write.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.ConfigWriter

/**
 * Factory for creating instances of [ConfigWriter]
 */
interface ConfigWriterFactory {

    /**
     * @return An instance of [ConfigWriter]
     */
    fun createWriter(destination: String, config: SmartConfig) : ConfigWriter
}