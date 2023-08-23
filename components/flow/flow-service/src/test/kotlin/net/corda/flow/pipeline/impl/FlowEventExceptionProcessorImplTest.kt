package net.corda.flow.pipeline.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.nio.ByteBuffer
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowEventExceptionProcessorImplTest {
    private val flowMessageFactory = mock<FlowMessageFactory>()
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowEventContextConverter = mock<FlowEventContextConverter>()
    private val flowSessionManager = mock<FlowSessionManager>()
    private val flowCheckpoint = mock<FlowCheckpoint>()

    private val flowConfig = ConfigFactory.empty()
        .withValue(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, ConfigValueFactory.fromAnyRef(2))
    private val smartFlowConfig = SmartConfigFactory.createWithoutSecurityServices().create(flowConfig)
    private val inputEvent = ExternalEventResponse()
    private val context = buildFlowEventContext<Any>(checkpoint = flowCheckpoint, inputEventPayload = inputEvent)
    private val converterResponse = StateAndEventProcessor.Response<Checkpoint>(
        null,
        listOf<Record<String, String>>()
    )
    private val flowFiberCache = mock<FlowFiberCache>()
    private val serializedFiber = ByteBuffer.wrap("mock fiber".toByteArray())

    private val sessionIdOpen = "sesh-id"
    private val sessionIdClosed = "sesh-id-closed"
    private val flowActiveSessionState = SessionState().apply {
        sessionId = sessionIdOpen
        status = SessionStateType.CONFIRMED
        hasScheduledCleanup = false
    }
    private val flowInactiveSessionState =
        SessionState().apply { sessionId = sessionIdClosed; status = SessionStateType.CLOSED; hasScheduledCleanup = true }

    private val target = FlowEventExceptionProcessorImpl(
        flowMessageFactory,
        flowRecordFactory,
        flowEventContextConverter,
        flowSessionManager,
        flowFiberCache
    )

    @BeforeEach
    fun setup() {
        target.configure(smartFlowConfig)
        whenever(flowEventContextConverter.convert(any())).thenReturn(converterResponse)
        whenever(flowCheckpoint.serializedFiber).thenReturn(serializedFiber)
    }

    @Test
    fun `unexpected exception`() {
        val error = IllegalStateException()

        val result = target.process(error)

        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).isEmpty()
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `flow transient exception sets retry state and publishes a status update`() {
        val error = FlowTransientException("error")
        val flowStatusUpdate = FlowStatus()
        val key = FlowKey()
        val flowStatusUpdateRecord = Record("", key, flowStatusUpdate)
        val flowId = "f1"
        val flowEventRecord = Record("", flowId, FlowEvent(flowId, ExternalEventResponse()))
        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.currentRetryCount).thenReturn(1)
        whenever(flowMessageFactory.createFlowRetryingStatusMessage(flowCheckpoint)).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        whenever(flowCheckpoint.flowKey).thenReturn(key)
        whenever(flowRecordFactory.createFlowEventRecord(flowId, ExternalEventResponse())).thenReturn(flowEventRecord)

        val result = target.process(error, context)

        assertThat(result).isSameAs(converterResponse)
        verify(flowEventContextConverter).convert(argThat {
            assertThat(this.outputRecords).containsOnly(flowStatusUpdateRecord, flowEventRecord)
            true
        }
        )
        verify(flowCheckpoint).rollback()
        verify(flowCheckpoint).markForRetry(context.inputEvent, error)
        verify(flowFiberCache).remove(key)
    }

    @Test
    fun `flow transient exception when doesExist false does not remove from flow fiber cache`() {
        val error = FlowTransientException("error")
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)
        val flowId = "f1"
        val flowEventRecord = Record("", flowId, FlowEvent(flowId, ExternalEventResponse()))
        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.currentRetryCount).thenReturn(1)
        whenever(flowMessageFactory.createFlowRetryingStatusMessage(flowCheckpoint)).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)
        whenever(flowRecordFactory.createFlowEventRecord(flowId, ExternalEventResponse())).thenReturn(flowEventRecord)
        whenever(flowCheckpoint.doesExist).thenReturn(false)

        val result = target.process(error, context)

        assertThat(result).isSameAs(converterResponse)
        verify(flowEventContextConverter).convert(argThat {
            assertThat(this.outputRecords).containsOnly(flowStatusUpdateRecord, flowEventRecord)
            true
        }
        )
        verify(flowCheckpoint).rollback()
        verify(flowCheckpoint).markForRetry(context.inputEvent, error)
        verify(flowFiberCache, times(0)).remove(any<List<FlowKey>>())
    }

    @Test
    fun `flow transient exception processed as fatal when retry limit reached`() {
        val error = FlowTransientException("mock error message")
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)
        whenever(flowCheckpoint.currentRetryCount).thenReturn(2)
        whenever(
            flowMessageFactory.createFlowFailedStatusMessage(
                flowCheckpoint,
                FlowProcessingExceptionTypes.FLOW_FAILED,
                "Execution failed with \"${error.message}\" after " +
                        "${flowConfig.getInt(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS)} retry attempts.",
            )
        ).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)

        val result = target.process(error, context)

        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).containsOnly(flowStatusUpdateRecord)
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `flow fatal exception marks flow for dlq and publishes status update`() {
        val error = FlowFatalException("error")
        val flowStatusUpdate = FlowStatus()
        val key = FlowKey()
        val flowStatusUpdateRecord = Record("", key, flowStatusUpdate)
        val flowMapperEvent = mock<FlowMapperEvent>()
        val flowMapperRecord = Record(Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC, "key", flowMapperEvent)

        whenever(
            flowMessageFactory.createFlowFailedStatusMessage(
                flowCheckpoint,
                FlowProcessingExceptionTypes.FLOW_FAILED,
                error.message
            )
        ).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        whenever(flowCheckpoint.flowKey).thenReturn(key)
        whenever(flowCheckpoint.sessions).thenReturn(listOf(flowActiveSessionState, flowInactiveSessionState))
        whenever(flowRecordFactory.createFlowMapperEventRecord(any(), any())).thenReturn(flowMapperRecord)

        val result = target.process(error, context)

        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).contains(flowStatusUpdateRecord, flowMapperRecord)
        assertThat(result.markForDLQ).isTrue
        verify(flowFiberCache).remove(key)
    }

    @Test
    fun `flow platform exception marks sets pending exception`() {
        val flowId = "f1"
        val error = FlowPlatformException("error")
        val key = FlowKey()

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        whenever(flowCheckpoint.flowKey).thenReturn(key)

        val result = target.process(error, context)

        assertThat(result).isSameAs(converterResponse)
        verify(flowEventContextConverter).convert(argThat {
            assertThat(this.outputRecords).isEmpty()
            true
        })

        verify(flowCheckpoint).waitingFor = WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        verify(flowCheckpoint).setPendingPlatformError(FlowProcessingExceptionTypes.PLATFORM_ERROR, error.message)
        verify(flowFiberCache).remove(key)
    }

    @Test
    fun `flow platform exception when doesExist false does not remove from flow fiber cache`() {
        val flowId = "f1"
        val error = FlowPlatformException("error")

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.doesExist).thenReturn(false)

        val result = target.process(error, context)

        assertThat(result).isSameAs(converterResponse)
        verify(flowEventContextConverter).convert(argThat {
            assertThat(this.outputRecords).isEmpty()
            true
        })

        verify(flowCheckpoint).waitingFor = WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        verify(flowCheckpoint).setPendingPlatformError(FlowProcessingExceptionTypes.PLATFORM_ERROR, error.message)
        verify(flowFiberCache, times(0)).remove(any<List<FlowKey>>())
    }

    @Test
    fun `flow exception outputs the transformed context as normal`() {
        val error = FlowEventException("error")

        val result = target.process(error, context)

        assertThat(result).isSameAs(converterResponse)
    }

    @Test
    fun `failure to create a status message does not prevent transient failure handling from succeeding`() {
        val error = FlowTransientException("error")
        val flowId = "f1"
        val flowEventRecord = Record("", flowId, FlowEvent(flowId, ExternalEventResponse()))
        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.currentRetryCount).thenReturn(1)
        whenever(flowMessageFactory.createFlowRetryingStatusMessage(flowCheckpoint)).thenThrow(IllegalStateException())
        whenever(flowRecordFactory.createFlowEventRecord(flowId, ExternalEventResponse())).thenReturn(flowEventRecord)

        val result = target.process(error, context)

        assertThat(result).isSameAs(converterResponse)
        verify(flowEventContextConverter).convert(argThat {
            assertThat(this.outputRecords).containsOnly(flowEventRecord)
            true
        }
        )
        verify(flowCheckpoint).rollback()
        verify(flowCheckpoint).markForRetry(context.inputEvent, error)
    }

    @Test
    fun `failure to create a status message does not prevent fatal failure handling from succeeding`() {
        val error = FlowFatalException("error")

        whenever(
            flowMessageFactory.createFlowFailedStatusMessage(
                flowCheckpoint,
                FlowProcessingExceptionTypes.FLOW_FAILED,
                error.message
            )
        ).thenThrow(IllegalStateException())

        val result = target.process(error, context)

        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).isEmpty()
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `throwable triggered during transient exception processing does not escape the processor`() {
        val throwable = RuntimeException()
        whenever(flowCheckpoint.currentRetryCount).thenReturn(1)
        whenever(flowMessageFactory.createFlowRetryingStatusMessage(flowCheckpoint)).thenThrow(throwable)

        val transientError = FlowTransientException("error")
        val transientResult = target.process(transientError, context)
        assertEmptyDLQdResult(transientResult)
    }

    @Test
    fun `throwable triggered during fatal exception processing does not escape the processor`() {
        val throwable = RuntimeException()
        val fatalError = FlowFatalException("error")
        whenever(
            flowMessageFactory.createFlowFailedStatusMessage(
                flowCheckpoint,
                FlowProcessingExceptionTypes.FLOW_FAILED,
                fatalError.message
            )
        ).thenThrow(throwable)
        val fatalResult = target.process(fatalError, context)
        assertEmptyDLQdResult(fatalResult)
    }

    @Test
    fun `throwable triggered during platform exception processing does not escape the processor`() {
        val throwable = RuntimeException()
        whenever(flowCheckpoint.setPendingPlatformError(any(), any())).thenThrow(throwable)
        val platformException = FlowPlatformException("error")
        val platformResult = target.process(platformException, context)
        assertEmptyDLQdResult(platformResult)
    }

    @Test
    fun `throwable triggered during event exception processing does not escape the processor`() {
        val throwable = RuntimeException()
        whenever(flowEventContextConverter.convert(context)).thenThrow(throwable)
        val eventError = FlowEventException("error")
        val eventResult = target.process(eventError, context)
        assertEmptyDLQdResult(eventResult)
    }

    @Test
    fun `flow fatal exception with false doesExist confirms flow checkpoint not called`() {
        val flowCheckpoint = mock<FlowCheckpoint>()
        whenever(flowCheckpoint.doesExist).thenReturn(false)

        val error = FlowFatalException("error")
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)

        whenever(
            flowMessageFactory.createFlowFailedStatusMessage(
                flowCheckpoint,
                FlowProcessingExceptionTypes.FLOW_FAILED,
                error.message
            )
        ).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)
        target.process(error, context)

        verify(flowCheckpoint, times(0)).flowStartContext
        verify(flowFiberCache, times(0)).remove(any<List<FlowKey>>())
    }

    @Test
    fun `flow fatal exception with true doesExist confirms flow checkpoint is called`() {
        val flowCheckpoint = mock<FlowCheckpoint>()
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        val context = buildFlowEventContext<Any>(checkpoint = flowCheckpoint, inputEventPayload = inputEvent)

        val error = FlowFatalException("error")
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)

        whenever(
            flowMessageFactory.createFlowFailedStatusMessage(
                flowCheckpoint,
                FlowProcessingExceptionTypes.FLOW_FAILED,
                error.message
            )
        ).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)
        target.process(error, context)

        verify(flowCheckpoint, times(1)).flowStartContext
    }

    private fun assertEmptyDLQdResult(result: StateAndEventProcessor.Response<Checkpoint>) {
        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).isEmpty()
        assertThat(result.markForDLQ).isTrue
    }
}