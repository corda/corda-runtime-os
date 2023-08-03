package net.corda.crypto.service.impl.bus

import net.corda.crypto.core.CryptoService
import net.corda.data.crypto.wire.ops.rewrap.CryptoRewrapRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record

class CryptoRewrapBusProcessor(
    val cryptoService: CryptoService
) : DurableProcessor<String, CryptoRewrapRequest> {


    override val keyClass: Class<String> = String::class.java
    override val valueClass = CryptoRewrapRequest::class.java
    override fun onNext(events: List<Record<String, CryptoRewrapRequest>>): List<Record<*, *>> {
        events.forEach {
            val request = it.value
            if (request != null) {
                cryptoService.rewrapWrappingKey(request.tenantId, request.targetAlias, request.newParentKeyAlias)
            }

        }
        return events
    }

}