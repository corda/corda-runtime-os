package net.corda.messagebus.kafka.config

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA_COMMON
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_PROPERTIES_COMMON
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [BusConfigMerger::class])
class KafkaConfigMergerImpl : BusConfigMerger {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        logger.debug("Merging boot config into messaging config")
        var updatedMessagingConfig = (messagingConfig ?: getBaseKafkaMessagingConfig(bootConfig))
            .withValue(BootConfig.INSTANCE_ID, ConfigValueFactory.fromAnyRef(bootConfig.getString(BootConfig.INSTANCE_ID)))
            .withValue(BootConfig.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(bootConfig.getString(BootConfig.TOPIC_PREFIX)))
            .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))

        val kafkaBootConfig = bootConfig.getConfig(BOOT_KAFKA_COMMON).entrySet()
        logger.debug("Looping through kafka boot config")
        kafkaBootConfig.forEach { entry ->
            logger.debug { "Entry key: ${entry.key}" }
            updatedMessagingConfig = updatedMessagingConfig.withValue(
                "$KAFKA_PROPERTIES_COMMON.${entry.key}",
                ConfigValueFactory.fromAnyRef(bootConfig.getString("$BOOT_KAFKA_COMMON.${entry.key}"))
            )
        }

        return updatedMessagingConfig
    }

    private fun getBaseKafkaMessagingConfig(bootConfig: SmartConfig): SmartConfig {
        val hasMaxMsgSize = bootConfig.hasPath(MAX_ALLOWED_MSG_SIZE)
        val baseKafkaConfig = SmartConfigImpl.empty()
            .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
        return if (hasMaxMsgSize) {
            baseKafkaConfig
                .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(bootConfig.getLong(BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE)))
        } else {
            baseKafkaConfig
        }
    }
}
