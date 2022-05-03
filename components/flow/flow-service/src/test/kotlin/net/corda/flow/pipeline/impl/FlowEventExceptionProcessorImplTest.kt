package net.corda.flow.pipeline.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.IllegalStateException

class FlowEventExceptionProcessorImplTest {

    private val flowMessageFactory = mock<FlowMessageFactory>()
    private val flowRecordFactory = mock<FlowRecordFactory>()
    private val flowEventContextConverter = mock<FlowEventContextConverter>()
    private val flowCheckpoint = mock<FlowCheckpoint>()

    private val flowConfig = ConfigFactory.empty()
        .withValue(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, ConfigValueFactory.fromAnyRef(2))
    private val smartFlowConfig = SmartConfigFactory.create(flowConfig).create(flowConfig)
    private val inputEvent = Wakeup()
    private val context = buildFlowEventContext<Any>(checkpoint =  flowCheckpoint, inputEventPayload =  inputEvent)
    private val converterResponse = StateAndEventProcessor.Response<Checkpoint>(
        null,
        listOf<Record<String, String>>()
    )

    private val target = FlowEventExceptionProcessorImpl(
        flowMessageFactory,
        flowRecordFactory,
        flowEventContextConverter
    )

    @BeforeEach
    fun setup() {
        target.configure(smartFlowConfig)
        whenever(flowEventContextConverter.convert(any())).thenReturn(converterResponse)
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
        val error = FlowTransientException("error", context)
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)
        whenever(flowCheckpoint.currentRetryCount).thenReturn(1)
        whenever(flowMessageFactory.createFlowRetryingStatusMessage(flowCheckpoint)).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)

        val result = target.process(error)

        assertThat(result).isSameAs(converterResponse)
        verify(flowEventContextConverter).convert(argThat{
            assertThat(this.outputRecords).containsOnly(flowStatusUpdateRecord)
            true
            }
        )
        verify(flowCheckpoint).rollback()
        verify(flowCheckpoint).markForRetry(context.inputEvent,error)
    }

    @Test
    fun `flow transient exception processed as fatal when retry limit reached`() {
        val error = FlowTransientException("error", context)
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)
        whenever(flowCheckpoint.currentRetryCount).thenReturn(2)
        whenever(flowMessageFactory.createFlowFailedStatusMessage(
            flowCheckpoint,
            FlowProcessingExceptionTypes.FLOW_FAILED,
            "Flow processing has failed due to a fatal exception, the flow will be moved to the DLQ"
        )).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)

        val result = target.process(error)

        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).containsOnly(flowStatusUpdateRecord)
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `flow fatal exception marks flow for dlq and publishes status update`() {
        val error = FlowFatalException("error", context)
        val flowStatusUpdate = FlowStatus()
        val flowStatusUpdateRecord = Record("", FlowKey(), flowStatusUpdate)

        whenever(flowMessageFactory.createFlowFailedStatusMessage(
            flowCheckpoint,
            FlowProcessingExceptionTypes.FLOW_FAILED,
            "Flow processing has failed due to a fatal exception, the flow will be moved to the DLQ"
        )).thenReturn(flowStatusUpdate)
        whenever(flowRecordFactory.createFlowStatusRecord(flowStatusUpdate)).thenReturn(flowStatusUpdateRecord)

        val result = target.process(error)

        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).containsOnly(flowStatusUpdateRecord)
        assertThat(result.markForDLQ).isTrue
    }

    @Test
    fun `flow flow exception outputs the transformed context as normal`() {
        val error = FlowEventException("error", context)

        val result = target.process(error)

        assertThat(result).isSameAs(converterResponse)
    }
}