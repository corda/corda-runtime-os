package net.corda.configuration.publish.impl

import net.corda.libs.configuration.dto.ConfigurationDto
import net.corda.configuration.publish.ConfigPublishService
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC

class ConfigPublishServiceImpl(
    private val publisher: Publisher
) : ConfigPublishService {

    override fun put(configDto: ConfigurationDto) {
        val (configSection, configAvro) = configDto.toAvro()

        // TODO - CORE-3404 - Check new config against current Kafka config to avoid overwriting.
        val futures = publisher.publish(listOf(Record(CONFIG_TOPIC, configSection, configAvro)))

        // TODO - CORE-3730 - Define timeout policy.
        futures.first().get()
    }
}