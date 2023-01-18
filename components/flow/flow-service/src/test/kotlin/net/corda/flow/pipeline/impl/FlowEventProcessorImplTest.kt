package net.corda.flow.pipeline.impl

import java.time.Instant
import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.MINIMUM_SMART_CONFIG
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowMDCService
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowEventProcessorImplTest {

    private val wakeupPayload = Wakeup()
    private val aliceHoldingIdentity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "1")
    private val bobHoldingIdentity = HoldingIdentity("CN=Bob, O=Alice Corp, L=LDN, C=GB", "1")
    private val startFlowEvent = StartFlow(
        FlowStartContext(
            FlowKey("flowId", aliceHoldingIdentity),
            FlowInitiatorType.RPC,
            "clientRequestId",
            aliceHoldingIdentity,
            "cpiId",
            aliceHoldingIdentity,
            "flowClassName",
            "startArgs",
            KeyValuePairList(),
            Instant.now()
        ),
        "flowStartArgs"
    )

    private val sessionInitFlowEvent =
        SessionEvent.newBuilder()
            .setInitiatingIdentity(aliceHoldingIdentity)
            .setInitiatedIdentity(bobHoldingIdentity)
            .setMessageDirection(MessageDirection.INBOUND)
            .setPayload(SessionInit())
            .setTimestamp(Instant.now())
            .setSessionId("SessionId")
            .setSequenceNum(1)
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(emptyList())
            .build()

    private val flowKey = "flow id"
    private val flowCheckpoint = mock<FlowCheckpoint>()
    private val checkpoint: Checkpoint = mock()
    private val flowState: FlowState = mock()
    private val flowStartContext: FlowStartContext = mock()
    private val externalEventState: ExternalEventState = mock()
    private val outputRecords = listOf(Record(FLOW_EVENT_TOPIC, "key", "value"))
    private val updatedContext = buildFlowEventContext<Any>(
        flowCheckpoint,
        wakeupPayload,
        outputRecords = outputRecords
    )
    private val flowKilledStatusRecords = listOf(Record(FLOW_STATUS_TOPIC, "key", flowState))

    private val outputResponse = StateAndEventProcessor.Response<Checkpoint>(
        null,
        listOf<Record<String, String>>()
    )

    private val detailsMap = mapOf("reason" to "answer")
    private val flowEventPipeline = mock<FlowEventPipeline>().apply {
        whenever(eventPreProcessing()).thenReturn(this)
        whenever(runOrContinue(any())).thenReturn(this)
        whenever(setCheckpointSuspendedOn()).thenReturn(this)
        whenever(setWaitingFor()).thenReturn(this)
        whenever(requestPostProcessing()).thenReturn(this)
        whenever(globalPostProcessing()).thenReturn(this)
        whenever(context).thenReturn(updatedContext)
    }
    private val flowEventExceptionProcessor = mock<FlowEventExceptionProcessor>()
    private val flowEventContextConverter = mock<FlowEventContextConverter>().apply {
        whenever(convert(updatedContext)).thenReturn(
            StateAndEventProcessor.Response(
                checkpoint,
                outputRecords
            )
        )
    }
    private val flowMDCService = mock<FlowMDCService>().apply {
        whenever(getMDCLogging(anyOrNull(), anyOrNull(), any())).thenReturn(
            emptyMap()
        )
    }

    private val flowEventPipelineFactory = mock<FlowEventPipelineFactory>().apply {
        whenever(create(anyOrNull(), any(), any(), any())).thenReturn(flowEventPipeline)
    }

    private val processor = FlowEventProcessorImpl(
        flowEventPipelineFactory,
        flowEventExceptionProcessor,
        flowEventContextConverter,
        MINIMUM_SMART_CONFIG,
        flowMDCService
    )

    @BeforeEach
    fun setup() {
        whenever(checkpoint.flowState).thenReturn(flowState)
        whenever(flowState.flowStartContext).thenReturn(flowStartContext)
        whenever(flowState.externalEventState).thenReturn(externalEventState)
        whenever(externalEventState.status).thenReturn(ExternalEventStateStatus(ExternalEventStateType.OK, null))
        whenever(externalEventState.requestId).thenReturn("externalEventId")
        whenever(flowStartContext.requestId).thenReturn("requestId")
        whenever(flowStartContext.identity).thenReturn(aliceHoldingIdentity)
    }

    @Test
    fun `Returns the state unaltered if no flow event supplied`() {
        val inputEvent = getFlowEventRecord(null)

        val response = processor.onNext(checkpoint, inputEvent)

        assertThat(response.updatedState).isSameAs(checkpoint)
        assertThat(response.responseEvents).isEmpty()
        verify(flowMDCService, times(0)).getMDCLogging(anyOrNull(), any(), any())
    }

    @Test
    fun `Returns a checkpoint and events to send`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, wakeupPayload))

        val response = processor.onNext(checkpoint, inputEvent)

        assertEquals(checkpoint, response.updatedState)
        assertEquals(outputRecords, response.responseEvents)
        verify(flowMDCService, times(1)).getMDCLogging(anyOrNull(), any(), any())
    }

    @Test
    fun `Calls the pipeline steps in order`() {
        processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))
        inOrder(flowEventPipeline) {
            verify(flowEventPipeline).eventPreProcessing()
            verify(flowEventPipeline).runOrContinue(any())
            verify(flowEventPipeline).setCheckpointSuspendedOn()
            verify(flowEventPipeline).setWaitingFor()
            verify(flowEventPipeline).requestPostProcessing()
            verify(flowEventPipeline).globalPostProcessing()
        }
    }

    @Test
    fun `Flow transient exception is handled`() {
        val error = FlowTransientException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(outputResponse)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow event exception is handled`() {
        val error = FlowEventException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(outputResponse)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow platform exception is handled`() {
        val error = FlowPlatformException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(outputResponse)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow fatal exception is handled`() {
        val error = FlowFatalException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(outputResponse)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `Flow unexpected exception is handled`() {
        val error = IllegalStateException()

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error)).thenReturn(outputResponse)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(outputResponse)
    }

    @Test
    fun `FlowMarkedForKillException produces flow kill context`() {
        val error = FlowMarkedForKillException("reason", detailsMap)

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        val killedFlowResponse = StateAndEventProcessor.Response(checkpoint, flowKilledStatusRecords)
        whenever(flowEventExceptionProcessor.process(error, updatedContext)).thenReturn(killedFlowResponse)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, wakeupPayload)))

        assertThat(response).isEqualTo(killedFlowResponse)
    }

    @Test
    fun `Execute flow pipeline from null checkpoint and start flow event`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, startFlowEvent))

        val response = processor.onNext(null, inputEvent)

        assertEquals(checkpoint, response.updatedState)
        assertEquals(outputRecords, response.responseEvents)
        verify(flowMDCService, times(1)).getMDCLogging(anyOrNull(), any(), any())
    }

    @Test
    fun `Execute flow pipeline from null checkpoint and session init event`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, sessionInitFlowEvent))

        val response = processor.onNext(null, inputEvent)

        assertEquals(checkpoint, response.updatedState)
        assertEquals(outputRecords, response.responseEvents)
        verify(flowMDCService, times(1)).getMDCLogging(anyOrNull(), any(), any())
    }

    private fun getFlowEventRecord(flowEvent: FlowEvent?): Record<String, FlowEvent> {
        return Record(FLOW_EVENT_TOPIC, flowKey, flowEvent)
    }
}