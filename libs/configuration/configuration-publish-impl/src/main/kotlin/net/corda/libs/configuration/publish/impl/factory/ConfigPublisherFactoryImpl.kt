package net.corda.libs.configuration.publish.impl.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.publish.ConfigPublisher
import net.corda.libs.configuration.publish.factory.ConfigPublisherFactory
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Kafka implementation for [ConfigPublisherFactory].
 * @property publisherFactory
 */
@Component(immediate = true, service = [ConfigPublisherFactory::class])
class ConfigPublisherFactoryImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ConfigPublisherFactory {

    companion object {
        private const val CONFIGURATION_WRITER = "CONFIGURATION_WRITER"
    }

    override fun createPublisher(destination: String, config: SmartConfig): ConfigPublisher {
        val publisher = publisherFactory.createPublisher(
            PublisherConfig(
                CONFIGURATION_WRITER
            ),
            config
        )
        return ConfigPublisherImpl(destination, publisher)
    }
}
