package net.corda.messaging.kafka.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Boot.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Bus.BOOTSTRAP_SERVER
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE

class IntegrationTestProperties {
    companion object {
        val BOOTSTRAP_SERVERS_VALUE = System.getProperty("BROKERS_ADDRS") ?: System.getenv("BROKERS_ADDRS") ?: "localhost:9092"
        val TEST_CONFIG = SmartConfigImpl.empty()
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
            .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
            .withValue(BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))

        val NON_TRANSACTIONAL_PUBLISHER_CONFIG =TEST_CONFIG
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(null))
    }
}
