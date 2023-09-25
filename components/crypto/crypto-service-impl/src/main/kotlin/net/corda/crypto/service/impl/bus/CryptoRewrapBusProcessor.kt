package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationResponse
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Crypto.REWRAP_MESSAGE_RESPONSE_TOPIC
import java.time.Instant

/**
 * This processor does actual re-wrapping of the keys.
 * Each Kafka message contains information about one key that needs to be re-wrapped.
 */
@Suppress("LongParameterList")
class CryptoRewrapBusProcessor(
    val cryptoService: CryptoService
) : DurableProcessor<String, IndividualKeyRotationRequest> {

    override val keyClass: Class<String> = String::class.java
    override val valueClass = IndividualKeyRotationRequest::class.java

    override fun onNext(events: List<Record<String, IndividualKeyRotationRequest>>): List<Record<*, *>> {
        val records = mutableListOf<Record<String, IndividualKeyRotationResponse>>()
        events.forEach {
            val request = it.value
            val startTimestamp = Instant.now()
            if (request != null) {
                cryptoService.rewrapWrappingKey(request.tenantId, request.oldKeyAlias, request.newKeyAlias)
            }
            val endTimestamp = Instant.now()

            val value = createRewrapResponse(
                    request!!.requestId,
                    request.tenantId,
                    request.oldKeyAlias,
                    request.newKeyAlias,
                    startTimestamp,
                    endTimestamp
                )
            records.add(Record(REWRAP_MESSAGE_RESPONSE_TOPIC, request.requestId, value))
        }
        return records
    }

    private fun createRewrapResponse(
        requestId: String,
        tenantId: String,
        oldKeyAlias: String,
        newKeyAlias: String,
        startTimestamp: Instant,
        endTimestamp: Instant,
    ): IndividualKeyRotationResponse =
        IndividualKeyRotationResponse(requestId, tenantId, oldKeyAlias, newKeyAlias, startTimestamp, endTimestamp)
}
