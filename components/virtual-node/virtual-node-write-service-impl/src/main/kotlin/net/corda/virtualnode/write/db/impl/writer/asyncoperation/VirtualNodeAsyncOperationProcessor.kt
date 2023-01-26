package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

internal class VirtualNodeAsyncOperationProcessor(
    private val virtualNodeUpgradeHandler: VirtualNodeAsyncOperationHandler<VirtualNodeUpgradeRequest>
) : DurableProcessor<String, VirtualNodeAsynchronousRequest> {

    private companion object {
        val log = contextLogger()
    }

    override fun onNext(events: List<Record<String, VirtualNodeAsynchronousRequest>>): List<Record<*, *>> {
        log.info("Received ${events.size} asynchronous virtual node operation requests.")
        events.map { record ->
            try {
                when (val typedRequest = record.value?.request) {
                    is VirtualNodeUpgradeRequest -> {
                        virtualNodeUpgradeHandler.handle(
                            record.value!!.timestamp,
                            record.value!!.requestId,
                            typedRequest
                        )
                    }
                    null -> log.warn("Received null payload for asynchronous virtual node operation not supported: $record")
                    else -> log.warn("Asynchronous virtual node operation not supported: $record")
                }
            } catch (e: Exception) {
                log.warn(
                    "Error while processing asynchronous virtual node operation key: ${record.key}, requestId: ${record.value?.requestId}",
                    e
                )
            }
        }
        return emptyList()
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<VirtualNodeAsynchronousRequest> = VirtualNodeAsynchronousRequest::class.java
}