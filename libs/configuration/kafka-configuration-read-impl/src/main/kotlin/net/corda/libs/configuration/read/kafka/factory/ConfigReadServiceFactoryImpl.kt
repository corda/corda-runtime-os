package net.corda.libs.configuration.read.kafka.factory

import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.ConfigRepository
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.libs.configuration.read.kafka.ConfigReadServiceImpl
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [ConfigReadServiceFactory::class])
class ConfigReadServiceFactoryImpl: ConfigReadServiceFactory {

    override fun createReadService(configRepository: ConfigRepository): ConfigReadService {
        return ConfigReadServiceImpl(configRepository)
    }
}
