package net.corda.virtualnode.write.db.impl.writer.asyncoperation

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger

internal class VirtualNodeAsyncOperationProcessor(
    private val requestHandlers: Map<Class<*>, VirtualNodeAsyncOperationHandler<*>>,
    private val logger: Logger
) : DurableProcessor<String, VirtualNodeAsynchronousRequest> {

    override fun onNext(events: List<Record<String, VirtualNodeAsynchronousRequest>>): List<Record<*, *>> {
        logger.debug("Received ${events.size} asynchronous virtual node operation requests.")
        events.forEach { handleEvent(it) }
        return emptyList()
    }

    @Suppress("unchecked_cast")
    private fun handleEvent(eventRecord: Record<String, VirtualNodeAsynchronousRequest>) {

        if (eventRecord.value == null) {
            logger.warn("Received a virtual node asynchronous operation record without a value: $eventRecord")
            return
        }

        val operation = eventRecord.value!!

        if (operation.request == null) {
            logger.warn("Received virtual node asynchronous operation without a request message: $operation")
            return
        }

        val request = operation.request!!
        val requestType = request.javaClass

        if (!requestHandlers.containsKey(requestType)) {
            logger.warn("Asynchronous virtual node operation not supported: $requestType")
            return
        }

        val handler: VirtualNodeAsyncOperationHandler<Any> =
            requestHandlers[requestType] as VirtualNodeAsyncOperationHandler<Any>

        try {
            handler.handle(operation.timestamp, operation.requestId, request)
        } catch (e: Exception) {
            logger.warn(
                "Error while processing virtual node asynchronous operation record: ${eventRecord}",
                e
            )
        }
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<VirtualNodeAsynchronousRequest> = VirtualNodeAsynchronousRequest::class.java
}