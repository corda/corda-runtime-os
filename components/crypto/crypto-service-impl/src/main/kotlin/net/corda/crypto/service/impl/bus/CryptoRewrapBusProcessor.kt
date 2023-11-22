package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import java.util.UUID

/**
 * This processor does actual re-wrapping of the keys.
 * Each Kafka message contains information about one key that needs to be re-wrapped.
 */
@Suppress("LongParameterList")
class CryptoRewrapBusProcessor(
    val cryptoService: CryptoService
) : DurableProcessor<UUID, IndividualKeyRotationRequest> {
    override val keyClass: Class<UUID> = UUID::class.java
    override val valueClass = IndividualKeyRotationRequest::class.java

    override fun onNext(events: List<Record<UUID, IndividualKeyRotationRequest>>): List<Record<*, *>> {
        events.mapNotNull { it.value }.map { request ->
            cryptoService.rewrapWrappingKey(request.tenantId, request.targetKeyAlias, request.newParentKeyAlias)
        }
        return emptyList()
    }
}
