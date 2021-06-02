package net.corda.libs.configuration.write.factory

import net.corda.libs.configuration.write.ConfigWriteService

/**
 * Factory for creating instances of [ConfigWriteService]
 */
interface CordaWriteServiceFactory {

    /**
     * @return An instance of [ConfigWriteService]
     */
    fun createWriteService(destination: String) : ConfigWriteService
}