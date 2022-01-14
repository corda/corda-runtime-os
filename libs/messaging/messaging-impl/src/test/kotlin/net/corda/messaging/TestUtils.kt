package net.corda.messaging

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.properties.ConfigProperties.Companion.CLIENT_ID_COUNTER
import net.corda.messaging.properties.ConfigProperties.Companion.GROUP
import net.corda.messaging.properties.ConfigProperties.Companion.TOPIC

const val TOPIC_PREFIX = "test"

fun createStandardTestConfig(): Config = ConfigFactory.parseResourcesAnySyntax("messaging-enforced.conf")
    .withValue(GROUP, ConfigValueFactory.fromAnyRef(GROUP))
    .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
    .withValue(CLIENT_ID_COUNTER, ConfigValueFactory.fromAnyRef(1))
    .withValue(TOPIC, ConfigValueFactory.fromAnyRef(TOPIC))
    .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("test"))
    .withFallback(ConfigFactory.parseResourcesAnySyntax("messaging-defaults.conf"))
    .resolve()

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

