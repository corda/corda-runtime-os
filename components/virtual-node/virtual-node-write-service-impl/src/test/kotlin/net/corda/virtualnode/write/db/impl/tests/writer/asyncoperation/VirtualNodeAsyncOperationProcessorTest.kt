package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationHandler
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.VirtualNodeAsyncOperationProcessor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import java.time.Instant
import java.time.temporal.ChronoUnit

class VirtualNodeAsyncOperationProcessorTest {

    private val handlerMap = mutableMapOf<Class<*>, VirtualNodeAsyncOperationHandler<*>>(
        RequestType1::class.java to mock<VirtualNodeAsyncOperationHandler<RequestType1>>(),
        RequestType2::class.java to mock<VirtualNodeAsyncOperationHandler<RequestType2>>(),
    )
    private val logger = mock<Logger>()
    private val processor = VirtualNodeAsyncOperationProcessor(handlerMap, logger)

    private val requestId = "r1"
    private val timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    @Test
    fun `requests should be dispatched to correct handler`() {
        val requestType1 = RequestType1()
        val requestType2 = RequestType2()
        val request1= createRequest(requestType1)
        val request2 = createRequest(requestType2)

        processor.onNext(
            listOf(
                Record("topic", "key1", request1)
            )
        )

        verify(handlerMap.getHandler<RequestType1>(),times(1)).handle(timestamp,requestId,requestType1)
        verify(handlerMap.getHandler<RequestType2>(), times(0)).handle(any(),any(),any())

        processor.onNext(
            listOf(
                Record("topic", "key1", request2)
            )
        )

        verify(handlerMap.getHandler<RequestType1>(),times(1)).handle(timestamp,requestId,requestType1)
        verify(handlerMap.getHandler<RequestType2>(), times(1)).handle(timestamp,requestId,requestType2)
    }

    @Test
    fun `exceptions thrown by handlers are caught and logged`() {
        val requestType1 = RequestType1()
        val request1= createRequest(requestType1)
        val virtualNodeUpgradeHandler = handlerMap.getHandler<RequestType1>()
        val error = IllegalArgumentException()

        whenever(virtualNodeUpgradeHandler.handle(any(), any(), any()))
            .thenThrow(error)

        processor.onNext(
            listOf(
                Record("topic", "key", request1)
            )
        )

        verify(logger).warn(
            argWhere {
                it.startsWith("Error while processing virtual node") },
            eq(error)
        )
    }

    private fun createRequest(payload: Any): VirtualNodeAsynchronousRequest {
        return VirtualNodeAsynchronousRequest.newBuilder()
            .setRequestId(requestId)
            .setTimestamp(timestamp)
            .setRequest(payload)
            .build()

    }

    @Suppress("unchecked_cast")
    inline fun <reified T : Any> Map<Class<*>, VirtualNodeAsyncOperationHandler<*>>.getHandler()
            : VirtualNodeAsyncOperationHandler<T> {
        return this[T::class.java] as VirtualNodeAsyncOperationHandler<T>
    }

    class RequestType1

    class RequestType2
}