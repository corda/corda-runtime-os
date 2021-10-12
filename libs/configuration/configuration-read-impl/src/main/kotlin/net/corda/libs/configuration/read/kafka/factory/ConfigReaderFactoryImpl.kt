package net.corda.libs.configuration.read.kafka.factory

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigReader
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.libs.configuration.read.kafka.ConfigReaderImpl
import net.corda.libs.configuration.read.kafka.ConfigRepository
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [ConfigReaderFactory::class])
class ConfigReaderFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : ConfigReaderFactory {

    override fun createReader(bootstrapConfig: Config): ConfigReader {
        return ConfigReaderImpl(ConfigRepository(), subscriptionFactory, bootstrapConfig)
    }
}
