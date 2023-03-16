package net.corda.flow.pipeline.handlers.waiting

import com.typesafe.config.ConfigValueFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.data.flow.state.waiting.external.ExternalEventResponse
import net.corda.flow.REQUEST_ID_1
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.external.events.impl.factory.ExternalEventFactoryMap
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.FlowConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalEventResponseWaitingForHandlerTest {

    private lateinit var context: FlowEventContext<Any>
    private val externalEventState = ExternalEventState()
    private val externalEventResponse = ExternalEventResponse(REQUEST_ID_1)

    private val checkpoint = mock<FlowCheckpoint>()
    private val config = SmartConfigImpl.empty()
        .withValue(FlowConfig.EXTERNAL_EVENT_MAX_RETRIES, ConfigValueFactory.fromAnyRef(1))

    private val externalEventManager = mock<ExternalEventManager>()
    private val externalEventFactoryMap = mock<ExternalEventFactoryMap>()
    private val externalEventFactory = mock<ExternalEventFactory<Any, Any, *>>()

    private val externalEventResponseWaitingForHandler = ExternalEventResponseWaitingForHandler(
        externalEventManager,
        externalEventFactoryMap
    )

    @BeforeEach
    fun beforeEach() {
        context = buildFlowEventContext(checkpoint, "payload", config)
        externalEventState.factoryClassName = "factory class name"
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventFactory.responseType).thenReturn(Any::class.java)
    }

    @Test
    fun `resumes the flow if the state's status is OK and a response has been received`() {
        val response = FlowOpsResponse()
        val result = "this is the result"

        externalEventState.status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        whenever(externalEventManager.hasReceivedResponse(externalEventState)).thenReturn(true)
        whenever(externalEventFactoryMap.get(externalEventState.factoryClassName)).thenReturn(externalEventFactory)
        whenever(externalEventManager.getReceivedResponse(externalEventState, Any::class.java)).thenReturn(response)
        whenever(externalEventFactory.resumeWith(checkpoint, response)).thenReturn(result)

        val continuation = externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        assertEquals(FlowContinuation.Run(result), continuation)
        verify(checkpoint).externalEventState = null
    }

    @Test
    fun `does not resume the flow if the state's status is OK and a response has not been received`() {
        externalEventState.status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        whenever(externalEventManager.hasReceivedResponse(externalEventState)).thenReturn(false)
        val continuation = externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        assertEquals(FlowContinuation.Continue, continuation)
        verify(checkpoint, never()).externalEventState = null
    }

    @Test
    fun `resumes the flow with an error if the state's status is RETRY and the max retries have been exceeded`() {
        externalEventState.status = ExternalEventStateStatus(
            ExternalEventStateType.RETRY,
            ExceptionEnvelope("type", "message")
        )
        externalEventState.retries = 2
        val continuation = externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        verify(checkpoint).externalEventState = null
    }

    @Test
    fun `resumes the flow with an error if the state's status is RETRY and the max retries have been reached`() {
        externalEventState.status = ExternalEventStateStatus(
            ExternalEventStateType.RETRY,
            ExceptionEnvelope("type", "message")
        )
        externalEventState.retries = 1
        val continuation = externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        verify(checkpoint).externalEventState = null
    }

    @Test
    fun `increases the retry count if the state's status is RETRY and the max retries have not been exceeded`() {
        externalEventState.status = ExternalEventStateStatus(
            ExternalEventStateType.RETRY,
            ExceptionEnvelope("type", "message")
        )
        externalEventState.retries = 0
        val continuation = externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        assertEquals(1, externalEventState.retries)
        assertEquals(FlowContinuation.Continue, continuation)
        verify(checkpoint, never()).externalEventState = null
    }

    @Test
    fun `resumes the flow with an error if the state's status is PLATFORM_ERROR`() {
        externalEventState.status = ExternalEventStateStatus(
            ExternalEventStateType.PLATFORM_ERROR,
            ExceptionEnvelope("type", "message")
        )
        val continuation = externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        assertInstanceOf(FlowContinuation.Error::class.java, continuation)
        verify(checkpoint).externalEventState = null
    }

    @Test
    fun `throws an exception if the state's status is FATAL_ERROR`() {
        externalEventState.status = ExternalEventStateStatus(
            ExternalEventStateType.FATAL_ERROR,
            ExceptionEnvelope("type", "message")
        )
        assertThrows<FlowFatalException> {
            externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        }
        verify(checkpoint, never()).externalEventState = null
    }

    @Test
    fun `throws an exception if the state's status type is null`() {
        externalEventState.status = ExternalEventStateStatus(null, null)
        assertThrows<FlowFatalException> {
            externalEventResponseWaitingForHandler.runOrContinue(context, externalEventResponse)
        }
        verify(checkpoint, never()).externalEventState = null
    }
}
