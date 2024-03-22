package net.corda.flow.external.events.impl.executor

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.flow.application.serialization.FlowSerializationService
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowIORequest
import net.corda.internal.serialization.SerializedBytesImpl
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertNotEquals

class ExternalEventExecutorImplTest {
    private lateinit var mockFlowFiberService: MockFlowFiberService
    private lateinit var serializationService: FlowSerializationService
    private lateinit var externalEventExecutorImpl: ExternalEventExecutorImpl

    private val capturedArguments = mutableListOf<FlowIORequest.ExternalEvent>()
    private val mockFactoryClass = mock<ExternalEventFactory<Any, Any, Any>>()::class.java


    @CordaSerializable
    data class Mock(val map: Map<String, String>)

    private val mockParams = Mock(mutableMapOf("test" to "parameters"))
    private val mockHash1 = mock<SecureHash>().apply {
        whenever(toHexString()).thenReturn("123")
    }
    private val mockHash2 = mock<SecureHash>().apply {
        whenever(toHexString()).thenReturn("456")
    }

    companion object {
        const val testFlowId: String = "static_flow_id"
    }

    @BeforeEach
    fun setup() {
        initializeMocks()
    }

    private fun initializeMocks(
        flowId: String = testFlowId,
        suspendCount: Int = 1
    ) {
        mockFlowFiberService = MockFlowFiberService()
        whenever(mockFlowFiberService.flowCheckpoint.flowId).thenReturn(flowId)
        whenever(mockFlowFiberService.flowCheckpoint.suspendCount).thenReturn(suspendCount)
        serializationService = mock<FlowSerializationService>().apply {
            whenever(serialize<Any>(anyOrNull())).doAnswer { inv ->
                SerializedBytesImpl(inv.getArgument<Any>(0).toString().toByteArray())
            }
        }

        // Capture arguments (ArgumentCaptor doesn't play nice with suspending functions)
        doAnswer { invocation ->
            val arg = invocation.getArgument<FlowIORequest<Any>>(0) as FlowIORequest.ExternalEvent
            capturedArguments.add(arg)
            return@doAnswer mock<ExternalEventResponse>()
        }.`when`(mockFlowFiberService.flowFiber).suspend<ExternalEventResponse>(any())

        externalEventExecutorImpl = ExternalEventExecutorImpl(mockFlowFiberService, serializationService)
    }

    @Test
    fun `execute suspends with expected FlowIORequest`() {
        val contextProperties = mapOf(
            "platformKey" to "platformValue",
            "userKey" to "userValue"
        )

        val expectedRequest = FlowIORequest.ExternalEvent(
            // This is hardcoded, but should be deterministic!
            "static_flow_id-1-Rv/Hk3fgtersGknFrgpJSsMr9kqPU9+RROLZiRTxyqo=",
            mockFactoryClass, mockParams, contextProperties
        )

        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)

        assertEquals(1, capturedArguments.size)
        assertEquals(expectedRequest, capturedArguments[0])
    }

    @Test
    fun `Suspending with the same parameters, flowId, and suspensionCount produces the same requestID`() {
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)

        assertEquals(2, capturedArguments.size)
        assertEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }

    @Test
    fun `Suspending with different parameters produces a different requestID`() {
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)
        externalEventExecutorImpl.execute(mockFactoryClass, Mock(mapOf("additional" to "data")))

        assertEquals(2, capturedArguments.size)
        assertNotEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }

    @Test
    fun `Suspending with a different flowId produces a different requestID`() {
        val flowId1 = UUID.randomUUID().toString()
        val flowId2 = UUID.randomUUID().toString()

        initializeMocks(flowId = flowId1)
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)

        initializeMocks(flowId = flowId2)
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)

        assertEquals(2, capturedArguments.size)
        assertNotEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }

    @Test
    fun `Suspending with a different suspensionCount produces a different requestID`() {
        initializeMocks(suspendCount = 2)
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)

        initializeMocks(suspendCount = 3)
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams)

        assertEquals(2, capturedArguments.size)
        assertNotEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }
}
