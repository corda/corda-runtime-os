package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

internal class VirtualNodeAsyncOperationProcessor(
    private val virtualNodeUpgradeHandler: VirtualNodeAsyncOperationHandler<VirtualNodeUpgradeRequest>
) : DurableProcessor<String, VirtualNodeAsynchronousRequest> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(events: List<Record<String, VirtualNodeAsynchronousRequest>>): List<Record<*, *>> {
        logger.info("Received ${events.size} asynchronous virtual node operation requests.")
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
                    null -> logger.warn("Received null payload for asynchronous virtual node operation not supported: $record")
                    else -> logger.warn("Asynchronous virtual node operation not supported: $record")
                }
            } catch (e: Exception) {
                logger.warn(
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