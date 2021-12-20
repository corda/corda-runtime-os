@file:Suppress("DEPRECATION")

package net.corda.libs.configuration.write.kafka.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.ConfigWriter
import net.corda.libs.configuration.write.factory.ConfigWriterFactory
import net.corda.libs.configuration.write.kafka.ConfigWriterImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Kafka implementation for [ConfigWriterFactory].
 * @property publisherFactory
 */
@Deprecated("Use `PersistentConfigWriterFactoryImpl` instead.")
@Component(immediate = true, service = [ConfigWriterFactory::class])
class ConfigWriterFactoryImpl @Activate constructor(
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ConfigWriterFactory {

    companion object {
        private const val CONFIGURATION_WRITER = "CONFIGURATION_WRITER"
    }

    override fun createWriter(destination: String, config: SmartConfig): ConfigWriter {
        val publisher = publisherFactory.createPublisher(
            PublisherConfig(
                CONFIGURATION_WRITER
            ),
            config
        )
        return ConfigWriterImpl(destination, publisher)
    }
}
