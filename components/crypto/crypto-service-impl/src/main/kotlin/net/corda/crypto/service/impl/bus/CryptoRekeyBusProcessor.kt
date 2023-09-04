package net.corda.crypto.service.impl.bus


import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

/**
 * This processor goes through the databases and find out what keys need re-wrapping.
 * It then posts a message to Kafka for each key needing re-wrapping with the tenant ID.
 */
class CryptoRekeyBusProcessor(
    val cryptoService: CryptoService
) : DurableProcessor<String, KeyRotationRequest> {

    override val keyClass: Class<String> = String::class.java
    override val valueClass = KeyRotationRequest::class.java
    override fun onNext(events: List<Record<String, KeyRotationRequest>>): List<Record<*, *>> {
        events.forEach {
            // Query the database to find out what keys need re-wrapping. For each of them, post message to Kafka
            // with correct topic.

        }
        // We want to publish list of Records to Kafka for each key to be rewrapped.
        return events
    }
}
