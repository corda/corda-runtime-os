package net.corda.flow.external.events.impl.executor

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowIORequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertNotEquals

class ExternalEventExecutorImplTest {
    private lateinit var mockFlowFiberService: MockFlowFiberService
    private lateinit var externalEventExecutorImpl: ExternalEventExecutorImpl

    private val capturedArguments = mutableListOf<FlowIORequest.ExternalEvent>()
    private val mockFactoryClass = mock<ExternalEventFactory<Any,Any,Any>>()::class.java
    private val mockParams = mutableMapOf("test" to "parameters")

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

        // Capture arguments (ArgumentCaptor doesn't play nice with suspending functions)
        doAnswer { invocation ->
            val arg = invocation.getArgument<FlowIORequest<Any>>(0) as FlowIORequest.ExternalEvent
            capturedArguments.add(arg)
            return@doAnswer mock<ExternalEventResponse>()
        }.`when`(mockFlowFiberService.flowFiber).suspend<ExternalEventResponse>(any())

        externalEventExecutorImpl = ExternalEventExecutorImpl(mockFlowFiberService)
    }

    @Test
    fun `execute suspends with expected FlowIORequest`() {
        val contextProperties = mapOf(
            "platformKey" to "platformValue",
            "userKey" to "userValue"
        )

        val expectedRequest = FlowIORequest.ExternalEvent(
            // This is hardcoded, but should be deterministic!
            "26ec4282-eebc-358d-9ac3-6e32ca1b103b",
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
        externalEventExecutorImpl.execute(mockFactoryClass, mockParams + mapOf("additional" to "data"))

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
