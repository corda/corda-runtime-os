package net.corda.libs.configuration.read.factory

import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.ConfigRepository

interface ConfigReadServiceFactory {

    fun createReadService(configRepository: ConfigRepository): ConfigReadService
}