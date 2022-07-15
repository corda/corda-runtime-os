package net.corda.flow.external.events.handler

import org.apache.avro.specific.SpecificRecord

data class ExternalEventRecord(val topic: String, val key: Any?, val payload: SpecificRecord) {
    constructor(topic: String, payload: SpecificRecord) : this(topic, null, payload)
}