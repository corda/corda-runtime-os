package net.corda.messaging.kafka.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Boot.TOPIC_PREFIX
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.framework.FrameworkUtil

class IntegrationTestProperties {
    companion object {
        val BOOTSTRAP_SERVERS_VALUE = System.getProperty("BROKERS_ADDRS") ?: System.getenv("BROKERS_ADDRS") ?: "localhost:9092"
        private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
        val TEST_CONFIG = getResourceConfig("test.conf")
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))

        /**
         * Retrieve a resource from this bundle and convert it to a SmartConfig object.
         */
        private fun getResourceConfig(resource: String): SmartConfig {
            val bundle = FrameworkUtil.getBundle(this::class.java)
            val url = bundle?.getResource(resource)
                ?: throw CordaRuntimeException(
                    "Failed to get resource $resource from bundle"
                )
            val config = ConfigFactory.parseURL(url)
            return smartConfigFactory.create(config)
        }
    }


}
