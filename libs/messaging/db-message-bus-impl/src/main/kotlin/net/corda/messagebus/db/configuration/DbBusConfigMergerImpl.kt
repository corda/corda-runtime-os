package net.corda.messagebus.db.configuration

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.messagebus.api.configuration.getStringOrDefault
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import org.osgi.service.component.annotations.Component

@Component(service = [BusConfigMerger::class])
class DbBusConfigMergerImpl : BusConfigMerger {

    override fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        val updatedMessagingConfig = messagingConfig?: SmartConfigImpl.empty()
        return updatedMessagingConfig
            .withValue(MessagingConfig.Bus.DB_JDBC_URL,
                ConfigValueFactory.fromAnyRef(bootConfig.getStringOrNull(BootConfig.BOOT_JDBC_URL + "_messagebus")))
            .withValue(MessagingConfig.Bus.DB_USER,
                ConfigValueFactory.fromAnyRef(bootConfig.getStringOrDefault(BootConfig.BOOT_JDBC_USER, "")))
            .withValue(MessagingConfig.Bus.DB_PASS,
                ConfigValueFactory.fromAnyRef(bootConfig.getStringOrDefault(BootConfig.BOOT_JDBC_PASS, "")))
            .withValue(MessagingConfig.Bus.BUS_TYPE, ConfigValueFactory.fromAnyRef("DATABASE"))
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(bootConfig.getString(INSTANCE_ID)))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(bootConfig.getStringOrDefault(TOPIC_PREFIX, "")))
            .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(bootConfig.getLong(BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE)))
    }
}