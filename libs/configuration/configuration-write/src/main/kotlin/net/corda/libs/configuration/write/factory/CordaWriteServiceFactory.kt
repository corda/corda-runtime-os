package net.corda.libs.configuration.write.factory

import net.corda.libs.configuration.write.CordaWriteService

/**
 * Factory for creating instances of [CordaWriteService]
 */
interface CordaWriteServiceFactory {

    /**
     * @return An instance of [CordaWriteService]
     */
    fun getWriteService(destination: String) : CordaWriteService
}