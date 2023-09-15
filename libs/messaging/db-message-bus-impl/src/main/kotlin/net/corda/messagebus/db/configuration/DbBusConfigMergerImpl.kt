package net.corda.messagebus.db.configuration

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.messagebus.api.configuration.getConfigOrEmpty
import net.corda.messagebus.api.configuration.getStringOrDefault
import net.corda.messagebus.api.configuration.getStringOrNull
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.Bus
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import org.osgi.service.component.annotations.Component

@Component(service = [BusConfigMerger::class])
class DbBusConfigMergerImpl : BusConfigMerger {

    override fun getMessagingConfig(bootConfig: SmartConfig, messagingConfig: SmartConfig?): SmartConfig {
        var updatedMessagingConfig = messagingConfig ?: SmartConfigImpl.empty()

        bootConfig.getConfigOrEmpty(BootConfig.BOOT_STATE_MANAGER).entrySet().forEach { entry ->
            updatedMessagingConfig = updatedMessagingConfig.withValue(
                "${MessagingConfig.StateManager.STATE_MANAGER}.${entry.key}",
                fromAnyRef(bootConfig.getString("${BootConfig.BOOT_STATE_MANAGER}.${entry.key}"))
            )
        }

        return updatedMessagingConfig
            .withValue(INSTANCE_ID, fromAnyRef(bootConfig.getString(INSTANCE_ID)))
            .withValue(TOPIC_PREFIX, fromAnyRef(bootConfig.getStringOrDefault(TOPIC_PREFIX, "")))
            .withValue(MAX_ALLOWED_MSG_SIZE, fromAnyRef(bootConfig.getLong(BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE)))

            // Cluster Database
            .withValue(Bus.BUS_TYPE, fromAnyRef("DATABASE"))
            .withValue(Bus.DB_USER, fromAnyRef(bootConfig.getStringOrDefault(BootConfig.BOOT_JDBC_USER, "")))
            .withValue(Bus.DB_PASS, fromAnyRef(bootConfig.getStringOrDefault(BootConfig.BOOT_JDBC_PASS, "")))
            .withValue(
                Bus.DB_JDBC_URL,
                fromAnyRef(bootConfig.getStringOrNull(BootConfig.BOOT_JDBC_URL + "_messagebus"))
            )
    }
}
