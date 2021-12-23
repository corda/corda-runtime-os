package net.corda.messaging.utils

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.properties.ConfigProperties.Companion.PATTERN_PUBLISHER
import net.corda.messaging.properties.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.utils.ConfigUtils.Companion.resolvePublisherConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigUtilsTest {

    @Test
    fun testMergeConfig() {
        val config = ConfigFactory.empty()
            .withValue("a.b.key1", ConfigValueFactory.fromAnyRef("value1"))
            .withValue("a.b.key2.key2", ConfigValueFactory.fromAnyRef("value2"))
            .withValue("a.b.key3", ConfigValueFactory.fromAnyRef("value3"))
            .withValue("a.c.key4", ConfigValueFactory.fromAnyRef("value4"))
            .withValue("a.key5", ConfigValueFactory.fromAnyRef("value5"))

        val override = mapOf("key3" to "overrideValue3" )

        val properties = mergeProperties(config, "a.b", override)
        assertThat(properties["key1"]).isEqualTo("value1")
        assertThat(properties["key2.key2"]).isEqualTo("value2")
        assertThat(properties["key3"]).isEqualTo("overrideValue3")
        assertThat(properties["a.c.key4"]).isEqualTo(null)
        assertThat(properties["key4"]).isEqualTo(null)
        assertThat(properties["a.key5"]).isEqualTo(null)
        assertThat(properties["key5"]).isEqualTo(null)
    }

    @Test
    fun `check resolved publisher configuration is correct`() {
        val nodeConfig = SmartConfigImpl.empty()
            .withValue("messaging.topic.prefix", ConfigValueFactory.fromAnyRef("demo"))
        val publisherConfig = resolvePublisherConfiguration(
            PublisherConfig("clientId", 1).toConfig(),
            nodeConfig,
            1,
            PATTERN_PUBLISHER
        )
        assertThat(publisherConfig.getString(PRODUCER_TRANSACTIONAL_ID)).isEqualTo("clientId-1")
    }

    @Test
    fun `check resolved publisher configuration without instance id doesn't have transactional id`() {
        val nodeConfig = SmartConfigImpl.empty()
            .withValue("messaging.topic.prefix", ConfigValueFactory.fromAnyRef("demo"))
        val publisherConfig = resolvePublisherConfiguration(
            PublisherConfig("clientId").toConfig(),
            nodeConfig,
            1,
            PATTERN_PUBLISHER
        )
        assertThat(!publisherConfig.hasPath(PRODUCER_TRANSACTIONAL_ID))
    }
}
