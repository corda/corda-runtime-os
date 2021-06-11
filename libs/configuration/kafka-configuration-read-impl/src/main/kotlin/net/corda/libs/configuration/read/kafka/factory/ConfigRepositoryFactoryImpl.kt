package net.corda.libs.configuration.read.kafka.factory

import net.corda.libs.configuration.read.ConfigRepository
import net.corda.libs.configuration.read.factory.ConfigRepositoryFactory
import net.corda.libs.configuration.read.kafka.ConfigRepositoryImpl
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [ConfigRepositoryFactory::class])
class ConfigRepositoryFactoryImpl : ConfigRepositoryFactory {
    override fun createRepository(): ConfigRepository {
        return ConfigRepositoryImpl()
    }
}