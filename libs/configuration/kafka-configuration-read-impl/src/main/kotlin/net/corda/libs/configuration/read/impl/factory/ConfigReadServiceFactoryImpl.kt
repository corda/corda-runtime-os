package net.corda.libs.configuration.read.impl.factory

import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.libs.configuration.read.impl.ConfigReadServiceImpl
import net.corda.libs.configuration.read.impl.ConfigRepository
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [ConfigReadServiceFactory::class])
class ConfigReadServiceFactoryImpl(
    @Reference(service = ConfigRepository::class)
    private val configRepository: ConfigRepository
) : ConfigReadServiceFactory {

    override fun createReadService(): ConfigReadService {
        return ConfigReadServiceImpl(configRepository.getConfiguration())
    }


}
