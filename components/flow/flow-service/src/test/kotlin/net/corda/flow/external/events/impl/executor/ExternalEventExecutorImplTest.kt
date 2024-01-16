package net.corda.flow.external.events.impl.executor

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.impl.FlowCheckpointImpl
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

    @BeforeEach
    fun setup() {
        initializeMocks()
    }

    private fun initializeMocks(
        flowCheckpoint: FlowCheckpoint = mock()
    ) {
        mockFlowFiberService = MockFlowFiberService().apply {
            this.flowCheckpoint = flowCheckpoint
        }

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
        val flowId = UUID.randomUUID().toString()
        val suspendCount = 3

        val expectedRequest = FlowIORequest.ExternalEvent(
            // This is hardcoded, but should be deterministic!
            "cd7ddb81-17d5-3735-941c-dc4be70d2cee",
            mockFactoryClass, listOf("TEST"),
            mapOf("platformKey" to "platformValue", "userKey" to "userValue")
        )

        val flowCheckpoint: FlowCheckpoint = mock()

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.suspendCount).thenReturn(suspendCount)

        initializeMocks(flowCheckpoint)

        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST"))

        assertEquals(1, capturedArguments.size)
        assertEquals(capturedArguments[0], expectedRequest)
    }

    @Test
    fun `Suspending with the same parameters, flowId, and suspensionCount produces the same requestID`() {
        val flowId = UUID.randomUUID().toString()
        val suspendCount = 3

        val flowCheckpoint: FlowCheckpoint = mock()

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.suspendCount).thenReturn(suspendCount)

        initializeMocks(flowCheckpoint)

        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST"))
        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST"))

        assertEquals(2, capturedArguments.size)
        assertEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }

    @Test
    fun `Suspending with different parameters produces a different requestID`() {
        val flowId = UUID.randomUUID().toString()
        val suspendCount = 3

        val flowCheckpoint: FlowCheckpoint = mock()
        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.suspendCount).thenReturn(suspendCount)

        initializeMocks(flowCheckpoint)

        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST-1"))
        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST-2"))

        assertEquals(2, capturedArguments.size)
        assertNotEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }


    @Test
    fun `Suspending with a different flowId produces a different requestID`() {
        val flowId = UUID.randomUUID().toString()
        val suspendCount = 3

        val flowCheckpoint: FlowCheckpointImpl = mock()

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.suspendCount).thenReturn(suspendCount)

        initializeMocks(flowCheckpoint)

        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST"))

        whenever(flowCheckpoint.flowId).thenReturn(UUID.randomUUID().toString())
        initializeMocks(flowCheckpoint)

        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST"))

        assertEquals(2, capturedArguments.size)
        assertNotEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }

    @Test
    fun `Suspending with a different suspensionCount produces a different requestID`() {
        val flowId = UUID.randomUUID().toString()
        val suspendCount = 3

        val flowCheckpoint: FlowCheckpointImpl = mock()

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.suspendCount).thenReturn(suspendCount)

        initializeMocks(flowCheckpoint)

        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST"))

        whenever(flowCheckpoint.suspendCount).thenReturn(suspendCount + 1)
        initializeMocks(flowCheckpoint)

        externalEventExecutorImpl.execute(mockFactoryClass, listOf("TEST"))

        assertEquals(2, capturedArguments.size)
        assertNotEquals(capturedArguments[0].requestId, capturedArguments[1].requestId)
    }
}