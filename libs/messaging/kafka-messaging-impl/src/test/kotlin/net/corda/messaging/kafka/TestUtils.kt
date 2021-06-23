package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CLIENT_ID_COUNTER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.GROUP
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.INSTANCE_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC

const val TOPIC_PREFIX = "test"

fun createStandardTestConfig(): Config = ConfigFactory.parseResourcesAnySyntax("messaging-enforced.conf")
    .withValue(GROUP, ConfigValueFactory.fromAnyRef(GROUP))
    .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
    .withValue(CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(1))
    .withValue(TOPIC, ConfigValueFactory.fromAnyRef(TOPIC))
    .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("test"))
    .withFallback(ConfigFactory.parseResourcesAnySyntax("messaging-defaults.conf"))
    .resolve()

