package net.corda.tools.setup.common

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.schema.Schemas.Companion.CONFIG_TOPIC
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger

class ConfigHelper {
    companion object {
        private val log: Logger = contextLogger()
        private const val DEFAULT_BOOTSTRAP_SERVER_VALUE = "localhost:9092"

        const val SYSTEM_ENV_CONFIG_TOPIC_PATH = "config.topic"
        const val SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH = "bootstrap.servers"

        private const val GENERAL_CONFIG_CONFIG_TOPIC_PATH = "config.topic.name"
        private const val GENERAL_CONFIG_INSTANCE_ID_PATH = "instance-id"
        private const val TOPIC_MESSAGE_PREFIX_PATH = "messaging.topic.prefix"
        private const val KAFKA_CONFIG_BOOTSTRAP_SERVER_PATH = "messaging.kafka.common.bootstrap.servers"

        fun getBootstrapConfig(instanceId: Int?): Config {
            return ConfigFactory.empty()
                .withValue(
                    KAFKA_CONFIG_BOOTSTRAP_SERVER_PATH,
                    ConfigValueFactory.fromAnyRef(getConfigValue(SYSTEM_ENV_BOOTSTRAP_SERVERS_PATH, DEFAULT_BOOTSTRAP_SERVER_VALUE))
                )
                .withValue(
                    GENERAL_CONFIG_CONFIG_TOPIC_PATH,
                    ConfigValueFactory.fromAnyRef(getConfigValue(SYSTEM_ENV_CONFIG_TOPIC_PATH, CONFIG_TOPIC))
                )
                .withValue(TOPIC_MESSAGE_PREFIX_PATH, ConfigValueFactory.fromAnyRef(getConfigValue(TOPIC_MESSAGE_PREFIX_PATH, "")))
                .withValue(GENERAL_CONFIG_INSTANCE_ID_PATH, ConfigValueFactory.fromAnyRef(instanceId))
        }

        fun getConfigValue(path: String, default: String? = null): String {
            val configValue = System.getProperty(path)

            if (configValue == null) {
                if (default != null) {
                    return default
                }
                log.error(
                    "No $path property found! " +
                            "Pass property in via -D$path"
                )
            }

            return configValue
        }
    }
}
