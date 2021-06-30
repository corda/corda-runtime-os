package net.corda.libs.configuration.write.factory

import com.typesafe.config.Config
import net.corda.libs.configuration.write.ConfigWriteService

/**
 * Factory for creating instances of [ConfigWriteService]
 */
interface ConfigWriteServiceFactory {

    /**
     * @return An instance of [ConfigWriteService]
     */
    fun createWriteService(destination: String, config: Config) : ConfigWriteService
}