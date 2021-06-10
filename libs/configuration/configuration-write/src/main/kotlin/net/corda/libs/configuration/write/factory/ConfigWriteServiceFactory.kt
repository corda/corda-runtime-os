package net.corda.libs.configuration.write.factory

import net.corda.libs.configuration.write.ConfigWriteService
import java.util.*

/**
 * Factory for creating instances of [ConfigWriteService]
 */
interface ConfigWriteServiceFactory {

    /**
     * @return An instance of [ConfigWriteService]
     */
    fun createWriteService(destination: String, kafkaProperties: Properties) : ConfigWriteService
}