package net.corda.messaging

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.SubscriptionType
import java.time.Duration

const val TOPIC_PREFIX = "test"
const val TOPIC = "topic"
const val GROUP = "group"

internal fun createResolvedSubscriptionConfig(type: SubscriptionType): ResolvedSubscriptionConfig {
    val config = ConfigFactory.empty()
        .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("test"))
    val messageBusConfig = SmartConfigFactory.createWithoutSecurityServices().create(config)
    return ResolvedSubscriptionConfig(
        type,
        TOPIC,
        GROUP,
        1L,
        1,
        Duration.ofMillis(100L),
        Duration.ofMillis(100L),
        3,
        3,
        3,
        Duration.ofMillis(1000L),
        messageBusConfig
    )
}

/**
 * Generate a list of size [numberOfRecords] ConsumerRecords.
 * Assigned to [partition] and [topic]
 * @return List of ConsumerRecord
 */
fun generateMockCordaConsumerRecordList(numberOfRecords: Long, topic: String, partition: Int) : List<CordaConsumerRecord<String, String>> {
    val records = mutableListOf<CordaConsumerRecord<String, String>>()
    for (i in 0 until numberOfRecords) {
        val record = CordaConsumerRecord(topic, partition, i, "key$i", "value$i", i)
        records.add(record)
    }
    return records
}

