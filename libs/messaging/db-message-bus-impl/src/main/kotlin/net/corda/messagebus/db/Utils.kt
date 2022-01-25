package net.corda.messagebus.db

import net.corda.messagebus.db.util.CordaProducerRecord
import net.corda.messaging.api.records.Record

fun CordaProducerRecord<*, *>.toCordaRecord() = Record(topic, key, value)
fun List<CordaProducerRecord<*, *>>.toCordaRecords() = map { it.toCordaRecord() }
