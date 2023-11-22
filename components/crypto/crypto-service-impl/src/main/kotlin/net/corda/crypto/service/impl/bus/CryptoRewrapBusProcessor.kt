package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

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
        events.mapNotNull { it.value }.map { request ->
            cryptoService.rewrapWrappingKey(request.tenantId, request.targetKeyAlias, request.newParentKeyAlias)
        }
        return emptyList()
    }
}
