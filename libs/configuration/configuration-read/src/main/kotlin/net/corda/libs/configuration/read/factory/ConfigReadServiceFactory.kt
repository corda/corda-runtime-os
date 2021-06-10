package net.corda.libs.configuration.read.factory

import net.corda.libs.configuration.read.ConfigReadService

interface ConfigReadServiceFactory {

    fun createReadService() : ConfigReadService
}