package net.corda.messaging.kafka.integration

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import java.util.*

fun getDemoRecords(topic: String, recordCount: Int, keyCount: Int): List<Record<*, *>> {
    val records = mutableListOf<Record<*, *>>()
    for (i in 1..keyCount) {
        val key = "key$i"
        for (j in 1..recordCount) {
            records.add(Record(topic, key, DemoRecord(j)))
        }
    }
    return records
}

fun getStringRecords(topic: String, recordCount: Int, keyCount: Int): List<Record<*, *>> {
    val records = mutableListOf<Record<*, *>>()
    for (i in 1..keyCount) {
        val key = "key$i"
        for (j in 1..recordCount) {
            records.add(Record(topic, key, j.toString()))
        }
    }
    return records
}

fun getKafkaProperties(): Properties {
    val kafkaProperties = Properties()
    kafkaProperties[BOOTSTRAP_SERVERS_CONFIG] = IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE
    return kafkaProperties
}
