package net.corda.messaging.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX_VALUE
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil

class IntegrationTestProperties {
    companion object {
        val BOOTSTRAP_SERVERS_VALUE = System.getProperty("BROKERS_ADDRS") ?: System.getenv("BROKERS_ADDRS") ?: "localhost:9092"
        const val CLIENT_ID = "client.id"
        private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()
        val TEST_CONFIG = if (getBundleContext().isDBBundle()) {
            getResourceConfig("db.test.conf")
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
        } else {
            getResourceConfig("kafka.test.conf")
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX_VALUE))
        }

        /**
         * Retrieve a resource from this bundle and convert it to a SmartConfig object.
         */
        private fun getResourceConfig(resource: String): SmartConfig {
            val url = getBundle().getResource(resource)
                ?: throw CordaRuntimeException(
                    "Failed to get resource $resource from bundle"
                )
            val config = ConfigFactory.parseURL(url)
            return smartConfigFactory.create(config)
        }


        private fun getBundle(): Bundle {
            return FrameworkUtil.getBundle(this::class.java)
        }

        fun getBundleContext(): BundleContext {
            return getBundle().bundleContext
        }
    }
}
