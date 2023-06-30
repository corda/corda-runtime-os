package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.REQUEST_ID_1
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.utilities.seconds
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit

class ExternalEventResponseHandlerTest {

    private val externalEventResponse = ExternalEventResponse()

    private val checkpoint = mock<FlowCheckpoint>()
    private val clock = mock<Clock>()
    private val externalEventManager = mock<ExternalEventManager>()
    private val argumentCaptor = argumentCaptor<Int>()
    private val externalEventResponseHandler = ExternalEventResponseHandler(clock, externalEventManager)

    @Test
    fun `throws a flow event exception if the checkpoint does not exist`() {
        whenever(checkpoint.doesExist).thenReturn(false)

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        assertThrows<FlowEventException> {
            externalEventResponseHandler.preProcess(context)
        }
    }

    @Test
    fun `throws a flow event exception if the flow is not waiting for an external event response`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(null)

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        assertThrows<FlowEventException> {
            externalEventResponseHandler.preProcess(context)
        }
    }

    @Test
    fun `processes the received event if the flow is waiting for an external event response`() {
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        }
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.processResponse(externalEventState, externalEventResponse)).thenReturn(updatedExternalEventState)

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        externalEventResponseHandler.preProcess(context)
        verify(checkpoint).externalEventState = updatedExternalEventState
        verify(checkpoint, never()).setFlowSleepDuration(any())
    }

    @Test
    fun `sets the max flow sleep duration when the external event state is in a retry state`() {
        val now = Instant.now()
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.RETRY, null)
            sendTimestamp = now.plus(10, ChronoUnit.SECONDS)
        }
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.processResponse(externalEventState, externalEventResponse)).thenReturn(updatedExternalEventState)
        whenever(clock.instant()).thenReturn(now)
        doNothing().whenever(checkpoint).setFlowSleepDuration(argumentCaptor.capture())

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        externalEventResponseHandler.preProcess(context)
        verify(checkpoint).externalEventState = updatedExternalEventState
        verify(checkpoint).setFlowSleepDuration(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(10.seconds.toMillis().toInt())
    }

    @Test
    fun `sets the max flow sleep duration when the external event state is in a retry state and send timestamp is in the past`() {
        val now = Instant.now()
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply {
            requestId = REQUEST_ID_1
            status = ExternalEventStateStatus(ExternalEventStateType.RETRY, null)
            sendTimestamp = now.minus(10, ChronoUnit.SECONDS)
        }
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.processResponse(externalEventState, externalEventResponse)).thenReturn(updatedExternalEventState)
        whenever(clock.instant()).thenReturn(now)
        doNothing().whenever(checkpoint).setFlowSleepDuration(argumentCaptor.capture())

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        externalEventResponseHandler.preProcess(context)
        verify(checkpoint).externalEventState = updatedExternalEventState
        verify(checkpoint).setFlowSleepDuration(any())
        assertThat(argumentCaptor.firstValue).isEqualTo(0)
    }
}