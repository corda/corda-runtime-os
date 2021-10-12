package net.corda.libs.configuration.read.factory

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigReader

interface ConfigReaderFactory {

    /**
     * Create an instance of the [ConfigReader]
     * @param bootstrapConfig configuration object used to bootstrap the read service
     */
    fun createReader(bootstrapConfig: Config): ConfigReader
}
