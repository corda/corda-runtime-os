package net.corda.libs.configuration.read.kafka.factory

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.libs.configuration.read.kafka.ConfigReadServiceImpl
import net.corda.libs.configuration.read.kafka.ConfigRepository
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [ConfigReadServiceFactory::class])
class ConfigReadServiceFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : ConfigReadServiceFactory {

    override fun createReadService(bootstrapConfig: Config): ConfigReadService {
        return ConfigReadServiceImpl(ConfigRepository(), subscriptionFactory, bootstrapConfig)
    }
}
