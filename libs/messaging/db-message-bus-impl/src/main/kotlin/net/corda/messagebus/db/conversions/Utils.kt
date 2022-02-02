package net.corda.messagebus.db.conversions

import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.records.Record

fun CordaProducerRecord<*, *>.toCordaRecord() = Record(topic, key, value)
fun List<CordaProducerRecord<*, *>>.toCordaRecords() = map { it.toCordaRecord() }
