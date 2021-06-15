package net.corda.libs.configuration.read.factory

import net.corda.libs.configuration.read.ConfigReadService

interface ConfigReadServiceFactory {

    /**
     * Create an instance of the [ConfigReadService]
     */
    fun createReadService(): ConfigReadService
}
