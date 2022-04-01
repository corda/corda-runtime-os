package net.corda.messaging.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Boot.TOPIC_PREFIX

class IntegrationTestProperties {
    companion object {
        val BOOTSTRAP_SERVERS_VALUE = System.getProperty("BROKERS_ADDRS") ?: System.getenv("BROKERS_ADDRS") ?: "localhost:9092"
        const val CLIENT_ID = "client.id"
        private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
        val TEST_CONFIG = smartConfigFactory.create(ConfigFactory.load("test.conf"))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }
}
