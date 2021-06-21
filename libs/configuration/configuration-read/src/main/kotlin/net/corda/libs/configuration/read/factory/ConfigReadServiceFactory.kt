package net.corda.libs.configuration.read.factory

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigReadService

interface ConfigReadServiceFactory {

    /**
     * Create an instance of the [ConfigReadService]
     * @param bootstrapConfig configuration object used to bootstrap the read service
     */
    fun createReadService(bootstrapConfig: Config): ConfigReadService
}
