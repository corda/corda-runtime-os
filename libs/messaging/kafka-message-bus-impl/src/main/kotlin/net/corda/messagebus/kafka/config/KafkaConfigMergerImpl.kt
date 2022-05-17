package net.corda.messagebus.kafka.config

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

@Component(service = [BusConfigMerger::class])
class KafkaConfigMergerImpl : BusConfigMerger {

    private companion object {
        val logger = contextLogger()
    }

    override fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        logger.debug ("Merging boot config into messaging config")

        var updatedMessagingConfig = messagingConfig?: getBaseKafkaMessagingConfig(bootConfig)
        val kafkaBootConfig = bootConfig.getConfig(BOOT_KAFKA).entrySet()
        kafkaBootConfig.forEach { entry ->
            //todo make debug or remove
            logger.info("Looping through kafka boot config")
            logger.info("Entry: $entry")
            logger.info("Entry key: ${entry.key}")
            logger.info("Entry value: ${entry.value}")
            updatedMessagingConfig = updatedMessagingConfig.withValue(
                MessagingConfig.Bus.KAFKA_PROPERTIES_COMMON + entry.key, ConfigValueFactory.fromAnyRef(bootConfig.getString(entry.key))
            )
        }

        return updatedMessagingConfig
    }

    private fun getBaseKafkaMessagingConfig(bootConfig: SmartConfig): SmartConfig {
        return SmartConfigImpl.empty()
            .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(bootConfig.getString(INSTANCE_ID)))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(bootConfig.getString(TOPIC_PREFIX)))

    }
}
