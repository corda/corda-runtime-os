package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.api.configuration.ConfigProperties.Companion.PATTERN_PUBLISHER
import net.corda.messaging.api.configuration.ConfigProperties.Companion.PATTERN_PUBSUB
import net.corda.messaging.api.configuration.ConfigProperties.Companion.PATTERN_STATEANDEVENT
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.kafka.utils.ConfigUtils.Companion.resolvePublisherConfiguration
import net.corda.messaging.kafka.utils.ConfigUtils.Companion.resolveSubscriptionConfiguration
import net.corda.messaging.kafka.utils.toConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigTest {

    @Test
    fun `check resolved publisher configuration`() {
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

    @Test
    fun `check resolved subscription configuration`() {
        val subscriptionConfig = resolveSubscriptionConfiguration(
            SubscriptionConfig("group", "topic", 1).toConfig(),
            SmartConfigImpl.empty(),
            1,
            PATTERN_PUBSUB
        )

        assertThat(subscriptionConfig.getString(CONSUMER_GROUP_ID)).isEqualTo("topic-group")
        assertThat(subscriptionConfig.getString("consumer.client.id")).isEqualTo("topic-group-consumer-1")
    }

    @Test
    fun `check state and event config`() {
        val config = resolveSubscriptionConfiguration(
            SubscriptionConfig("group", "topic", 1).toConfig(),
            SmartConfigImpl.empty(),
            1,
            PATTERN_STATEANDEVENT
        )

        assertThat(config.getString("producer.client.id")).isEqualTo("group-producer-1")
        assertThat(config.getString("eventConsumer.client.id")).isEqualTo("topic-group-consumer-1")
        assertThat(config.getString("stateConsumer.client.id")).isEqualTo("topic.state-group-consumer-1")

        assertThat(config.getString("eventConsumer.group.id")).isEqualTo("topic-group")
        assertThat(config.getString("stateConsumer.group.id")).isEqualTo("topic-group")
    }
}
