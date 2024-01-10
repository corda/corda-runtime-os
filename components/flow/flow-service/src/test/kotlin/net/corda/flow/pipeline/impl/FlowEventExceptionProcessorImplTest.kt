package net.corda.flow.pipeline.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.maintenance.CheckpointCleanupHandler
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.events.FlowEventContext
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class FlowEventExceptionProcessorImplTest {
    private val flowMessageFactory = mock<FlowMessageFactory>()
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowEventContextConverter = mock<FlowEventContextConverter>()
    private val flowSessionManager = mock<FlowSessionManager>()
    private val flowCheckpoint = mock<FlowCheckpoint>()

    private val flowConfig = ConfigFactory.empty()
        .withValue(FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION, ConfigValueFactory.fromAnyRef(1000L))
    private val smartFlowConfig = SmartConfigFactory.createWithoutSecurityServices().create(flowConfig)
    private val inputEvent = ExternalEventResponse()
    private val context = buildFlowEventContext<Any>(checkpoint = flowCheckpoint, inputEventPayload = inputEvent)
    private val converterResponse = StateAndEventProcessor.Response<Checkpoint>(
        null,
        listOf<Record<String, String>>()
    )
    private val flowFiberCache = mock<FlowFiberCache>()
    private val serializedFiber = ByteBuffer.wrap("mock fiber".toByteArray())

    private val checkpointCleanupHandler = mock<CheckpointCleanupHandler>()

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
        flowSessionManager,
        flowFiberCache,
        checkpointCleanupHandler
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

        val result = target.process(error,context)

        verify(result.checkpoint).markDeleted()
        assertThat(result.sendToDlq).isTrue
        assertThat(result.outputRecords).isEmpty()
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
        whenever(flowCheckpoint.suspendCount).thenReturn(123)
        whenever(flowMessageFactory.createFlowRetryingStatusMessage(flowCheckpoint)).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        whenever(flowCheckpoint.flowKey).thenReturn(key)
        whenever(flowRecordFactory.createFlowEventRecord(flowId, ExternalEventResponse())).thenReturn(flowEventRecord)

        val result = target.process(error, context)

        verify(flowFiberCache).remove(key)
        verify(result.checkpoint).rollback()
        verify(result.checkpoint).markForRetry(context.inputEvent, error)
        assertThat(result.outputRecords).containsOnly(flowStatusUpdateRecord, flowEventRecord)
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

        verify(result.checkpoint).rollback()
        verify(result.checkpoint).markForRetry(context.inputEvent, error)
        assertThat(result.outputRecords).containsOnly(flowStatusUpdateRecord, flowEventRecord)
    }

    @Test
    fun `flow transient exception processed as fatal when retry window expired`() {
        val error = FlowTransientException("mock error message")
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)
        val retryCount = 2
        val now = Instant.now()
        whenever(flowCheckpoint.currentRetryCount).thenReturn(retryCount)
        whenever(flowCheckpoint.firstFailureTimestamp).thenReturn(now.minusMillis(2000))
        whenever(
            flowMessageFactory.createFlowFailedStatusMessage(
                flowCheckpoint,
                FlowProcessingExceptionTypes.FLOW_FAILED,
                "Execution failed with \"${error.message}\" after $retryCount retry attempts in a retry window of PT1S.",
            )
        ).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)

        target.process(error, context)

        verify(checkpointCleanupHandler).cleanupCheckpoint(eq(flowCheckpoint), any(), any<FlowFatalException>())
    }

    @Test
    fun `flow fatal exception marks flow for dlq and publishes status update`() {
        val flowId = "f1"
        val error = FlowFatalException("error")
        val key = FlowKey()
        val flowMapperEvent = mock<FlowMapperEvent>()
        val flowMapperRecord = Record(Schemas.Flow.FLOW_MAPPER_SESSION_OUT, "key", flowMapperEvent)
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        val startContext = mock<FlowStartContext>()
        whenever(flowCheckpoint.flowStartContext).thenReturn(startContext)
        whenever(flowCheckpoint.flowKey).thenReturn(key)
        val cleanupRecords = listOf (flowMapperRecord)
        whenever(checkpointCleanupHandler.cleanupCheckpoint(any(), any(), any())).thenReturn(cleanupRecords)

        val result = target.process(error, context)

        verify(checkpointCleanupHandler).cleanupCheckpoint(eq(flowCheckpoint), any(), eq(error))
        assertThat(result.outputRecords).containsOnly(flowMapperRecord)
        assertThat(result.sendToDlq).isTrue
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
        whenever(flowCheckpoint.suspendCount).thenReturn(123)

        val result = target.process(error, context)

        verify(flowCheckpoint).waitingFor = WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        verify(flowCheckpoint).setPendingPlatformError(FlowProcessingExceptionTypes.PLATFORM_ERROR, error.message)
        verify(flowFiberCache).remove(key)

        assertThat(result.outputRecords).isEmpty()
    }

    @Test
    fun `flow platform exception when doesExist false does not remove from flow fiber cache`() {
        val flowId = "f1"
        val error = FlowPlatformException("error")

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.doesExist).thenReturn(false)

        val result = target.process(error, context)

        verify(result.checkpoint).waitingFor = WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        verify(result.checkpoint).setPendingPlatformError(FlowProcessingExceptionTypes.PLATFORM_ERROR, error.message)
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

        verify(flowCheckpoint).rollback()
        verify(flowCheckpoint).markForRetry(context.inputEvent, error)
        assertThat(result.outputRecords).containsOnly(flowEventRecord)
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
        whenever(flowFiberCache.remove(flowCheckpoint.flowKey)).thenThrow(throwable)
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        val eventError = FlowEventException("error")
        val eventResult = target.process(eventError, context)
        assertEmptyDLQdResult(eventResult)
    }

    @Test
    fun `flow fatal exception with false doesExist confirms flow checkpoint not called`() {
        val flowCheckpoint = mock<FlowCheckpoint>()
        whenever(flowCheckpoint.doesExist).thenReturn(false)
        val error = FlowFatalException("error")

        target.process(error, context)

        verify(flowCheckpoint, times(0)).flowStartContext
    }

    @Test
    fun `flow fatal exception with true doesExist confirms flow checkpoint is called`() {
        val flowCheckpoint = mock<FlowCheckpoint>()
        whenever(flowCheckpoint.doesExist).thenReturn(true)
        val context = buildFlowEventContext<Any>(checkpoint = flowCheckpoint, inputEventPayload = inputEvent)

        val error = FlowFatalException("error")

        target.process(error, context)

        verify(flowCheckpoint, times(1)).flowStartContext
    }

    private fun assertEmptyDLQdResult(result: FlowEventContext<*>) {
        verify(result.checkpoint).markDeleted()
        assertThat(result.outputRecords).isEmpty()
        assertThat(result.sendToDlq).isTrue
    }
}