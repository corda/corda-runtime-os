package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

/**
 * This processor does actual re-wrapping of the keys.
 * Each Kafka message contains information about one key that needs to be re-wrapped.
 */
class CryptoRewrapBusProcessor(
    val cryptoService: CryptoService
) : DurableProcessor<String, IndividualKeyRotationRequest> {

    override val keyClass: Class<String> = String::class.java
    override val valueClass = IndividualKeyRotationRequest::class.java
    override fun onNext(events: List<Record<String, IndividualKeyRotationRequest>>): List<Record<*, *>> {
        events.forEach {
            val request = it.value
            if (request != null) {
                cryptoService.rewrapWrappingKey(request.tenantId, request.targetAlias, request.newParentKeyAlias)
            }
        }
        // We need to return something else - this is published back as a response on the incoming events
        return listOf()
    }
}
