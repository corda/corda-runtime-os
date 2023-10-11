package net.corda.flow.pipeline.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.external.ExternalEventResponse
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
import net.corda.flow.pipeline.handlers.FlowPostProcessingHandler
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class FlowEventProcessorImplTest {

    private val payload = ExternalEventResponse()
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
            .setContextSessionProperties(null)
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
        payload,
        outputRecords = outputRecords
    )
    private val errorContext = buildFlowEventContext<Any>(
        flowCheckpoint,
        payload,
        outputRecords = listOf(Record("error", "", ""))
    )

    private val outputResponse = StateAndEventProcessor.Response<Checkpoint>(
        null,
        listOf(Record("ok","",""))
    )

    private val errorResponse = StateAndEventProcessor.Response<Checkpoint>(
        null,
        listOf(Record("error","",""))
    )

    private val flowEventPipeline = mock<FlowEventPipeline>().apply {
        whenever(eventPreProcessing()).thenReturn(this)
        whenever(virtualNodeFlowOperationalChecks()).thenReturn(this)

        whenever(executeFlow(any())).thenReturn(this)
        whenever(globalPostProcessing()).thenReturn(this)
        whenever(context).thenReturn(updatedContext)
    }
    private val flowEventExceptionProcessor = mock<FlowEventExceptionProcessor>()
    private val flowEventContextConverter = mock<FlowEventContextConverter>().apply {
        whenever(convert(eq(updatedContext))).thenReturn(outputResponse)
        whenever(convert(eq(errorContext))).thenReturn(errorResponse)
    }

    private val flowMDCService = mock<FlowMDCService>().apply {
        whenever(getMDCLogging(anyOrNull(), anyOrNull(), any())).thenReturn(
            emptyMap()
        )
    }

    private val flowEventPipelineFactory = mock<FlowEventPipelineFactory>().apply {
        whenever(create(anyOrNull(), any(), any(), any(), any(), any())).thenReturn(flowEventPipeline)
    }

    private val flowPostProcessingHandler1 = mock<FlowPostProcessingHandler>()
    private val flowPostProcessingHandler2 = mock<FlowPostProcessingHandler>()
    private val flowPostProcessingHandlers = listOf(flowPostProcessingHandler1, flowPostProcessingHandler2)

    private val processor = FlowEventProcessorImpl(
        flowEventPipelineFactory,
        flowEventExceptionProcessor,
        flowEventContextConverter,
        mapOf(FLOW_CONFIG to MINIMUM_SMART_CONFIG),
        flowMDCService,
        flowPostProcessingHandlers
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
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, payload))

        val response = processor.onNext(checkpoint, inputEvent)

        val expectedRecords = updatedContext.outputRecords
        verify(flowEventContextConverter).convert(argThat { outputRecords == expectedRecords })
        assertThat(response).isEqualTo(outputResponse)

        verify(flowMDCService, times(1)).getMDCLogging(anyOrNull(), any(), any())
    }

    @Test
    fun `Calls the pipeline steps in order`() {
        processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, payload)))
        inOrder(flowEventPipeline) {
            verify(flowEventPipeline).eventPreProcessing()
            verify(flowEventPipeline).virtualNodeFlowOperationalChecks()
            verify(flowEventPipeline).executeFlow(any())
            verify(flowEventPipeline).globalPostProcessing()
        }
    }

    @Test
    fun `Flow transient exception is handled`() {
        val error = FlowTransientException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(errorContext)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, payload)))

        assertThat(response).isEqualTo(errorResponse)
    }

    @Test
    fun `Flow event exception is handled`() {
        val error = FlowEventException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(errorContext)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, payload)))

        assertThat(response).isEqualTo(errorResponse)
    }

    @Test
    fun `Flow platform exception is handled`() {
        val error = FlowPlatformException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(errorContext)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, payload)))

        assertThat(response).isEqualTo(errorResponse)
    }

    @Test
    fun `Flow fatal exception is handled`() {
        val error = FlowFatalException("")

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, flowEventPipeline.context)).thenReturn(errorContext)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, payload)))

        assertThat(response).isEqualTo(errorResponse)
    }

    @Test
    fun `Flow unexpected exception is handled`() {
        val error = IllegalStateException()

        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, updatedContext)).thenReturn(errorContext)

        val response = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, payload)))

        assertThat(response).isEqualTo(errorResponse)
    }

    @Test
    fun `FlowMarkedForKillException produces flow kill context`() {
        val error = FlowMarkedForKillException("reason")
        val flowKillErrorContext = buildFlowEventContext<Any>(
            flowCheckpoint,
            payload,
            outputRecords = listOf(Record("flowKill", "a", "a"))
        )
        val killErrorResponse = StateAndEventProcessor.Response<Checkpoint>(
            null,
            listOf(Record("killError","",""))
        )
        whenever(flowEventPipeline.eventPreProcessing()).thenThrow(error)
        whenever(flowEventExceptionProcessor.process(error, updatedContext)).thenReturn(flowKillErrorContext)
        whenever(flowEventContextConverter.convert(eq(flowKillErrorContext))).thenReturn(killErrorResponse)

        val result = processor.onNext(checkpoint, getFlowEventRecord(FlowEvent(flowKey, payload)))

        assertThat(result).isEqualTo(killErrorResponse)
    }

    @Test
    fun `Execute flow pipeline from null checkpoint and start flow event`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, startFlowEvent))

        val response = processor.onNext(null, inputEvent)

        assertThat(response).isEqualTo(outputResponse)
        verify(flowMDCService, times(1)).getMDCLogging(anyOrNull(), any(), any())
    }

    @Test
    fun `Execute flow pipeline from null checkpoint and session init event`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, sessionInitFlowEvent))

        val response = processor.onNext(null, inputEvent)

        assertThat(response).isEqualTo(outputResponse)
        verify(flowMDCService, times(1)).getMDCLogging(anyOrNull(), any(), any())
    }

    @Test
    fun `Flow event postprocessing handlers are called`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, sessionInitFlowEvent))

        val record1 = Record("1","","")
        val record2 = Record("2","","")
        val record3 = Record("3","","")

        whenever(flowPostProcessingHandler1.postProcess(updatedContext)).thenReturn(listOf(record1,record2))
        whenever(flowPostProcessingHandler2.postProcess(updatedContext)).thenReturn(listOf(record3))

        val expectedContext = updatedContext.copy(
            outputRecords = updatedContext.outputRecords + listOf(record1,record2,record3)
        )
        val responseWithPostProcessingRecords = StateAndEventProcessor.Response<Checkpoint>(
            null,
            listOf(Record("postprocessing","",""))
        )

        whenever(flowEventContextConverter.convert(eq(expectedContext))).thenReturn(responseWithPostProcessingRecords)

        val response = processor.onNext(null, inputEvent)

        assertThat(response).isEqualTo(responseWithPostProcessingRecords)
    }

    @Test
    fun `Flow event postprocessing handler errors don't prevent output`() {
        val inputEvent = getFlowEventRecord(FlowEvent(flowKey, sessionInitFlowEvent))

        val record1 = Record("1","","")
        val record2 = Record("2","","")

        whenever(flowPostProcessingHandler1.postProcess(updatedContext)).thenReturn(listOf(record1,record2))
        whenever(flowPostProcessingHandler2.postProcess(updatedContext)).thenThrow(IllegalArgumentException("error"))

        val expectedContext = updatedContext.copy(
            outputRecords = updatedContext.outputRecords + listOf(record1,record2)
        )
        val responseWithPostProcessingRecords = StateAndEventProcessor.Response<Checkpoint>(
            null,
            listOf(Record("postprocessing","",""))
        )

        whenever(flowEventContextConverter.convert(eq(expectedContext))).thenReturn(responseWithPostProcessingRecords)

        val response = processor.onNext(null, inputEvent)

        assertThat(response).isEqualTo(responseWithPostProcessingRecords)
    }

    private fun getFlowEventRecord(flowEvent: FlowEvent?): Record<String, FlowEvent> {
        return Record(FLOW_EVENT_TOPIC, flowKey, flowEvent)
    }
}