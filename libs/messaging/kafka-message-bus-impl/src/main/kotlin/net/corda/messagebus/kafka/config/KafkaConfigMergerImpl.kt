package net.corda.messagebus.kafka.config

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA_COMMON
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_PROPERTIES_COMMON
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component

@Component(service = [BusConfigMerger::class])
class KafkaConfigMergerImpl : BusConfigMerger {

    private companion object {
        val logger = contextLogger()
    }

    override fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        logger.debug ("Merging boot config into messaging config")
        var updatedMessagingConfig = (messagingConfig?: SmartConfigImpl.empty())

        val kafkaBootConfig = bootConfig.getConfig(BOOT_KAFKA_COMMON).entrySet()
        logger.debug("Looping through kafka boot config")
        kafkaBootConfig.forEach { entry ->
            logger.debug {"Entry key: ${entry.key}" }
            updatedMessagingConfig = updatedMessagingConfig.withValue(
                "$KAFKA_PROPERTIES_COMMON.${entry.key}",
                ConfigValueFactory.fromAnyRef(bootConfig.getString("$BOOT_KAFKA_COMMON.${entry.key}"))
            )
        }

        return updatedMessagingConfig
    }

    private fun getBaseKafkaMessagingConfig(): SmartConfig {
        return SmartConfigImpl.empty()
            .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
    }
}
