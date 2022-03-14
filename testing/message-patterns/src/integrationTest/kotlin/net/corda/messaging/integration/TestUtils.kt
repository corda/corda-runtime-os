package net.corda.messaging.integration

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.integration.IntegrationTestProperties.Companion.CLIENT_ID
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
    kafkaProperties[net.corda.messaging.integration.IntegrationTestProperties.BOOTSTRAP_SERVERS] = net.corda.messaging.integration.IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE
    kafkaProperties[CLIENT_ID] = "test"
    return kafkaProperties
}
