package net.corda.messaging.mediator

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Captor
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ProcessorTaskTest {

    companion object {
        private const val TEST_KEY = "key"
    }

    private data class StateType(val id: Int)
    private data class EventType(val id: String) {
        fun toRecord() = Record("", id, this)
    }

    private val processor = mock<StateAndEventProcessor<String, StateType, EventType>>()
    private val stateManagerHelper = mock<StateManagerHelper<String, StateType, EventType>>()

    @Captor
    private val stateCaptor = argumentCaptor<StateType>()

    @Captor
    private val eventCaptor = argumentCaptor<Record<String, EventType>>()

    @BeforeEach
    fun setup() {
        `when`(processor.onNext(anyOrNull(), any())).thenAnswer { invocation ->
            val state = invocation.getArgument<StateType>(0)
            val id = state?.let { it.id + 1 } ?: 0
            StateAndEventProcessor.Response(
                StateType(id),
                listOf(
                    EventType("outputEvent$id").toRecord()
                )
            )
        }

        `when`(stateManagerHelper.createOrUpdateState(any(), anyOrNull(), anyOrNull())).thenReturn(
            mock()
        )
    }

    @Test
    fun `successfully processes events`() {

        val persistedState: State? = null
        val eventIds = (1..3).toList()
        val inputEvents = eventIds.map { id -> EventType("inputEvent$id") }
        val inputEventRecords = inputEvents.map(EventType::toRecord)

        val task = ProcessorTask(
            TEST_KEY,
            persistedState,
            inputEventRecords,
            processor,
            stateManagerHelper,
        )

        val result = task.call()

        verify(processor, times(inputEventRecords.size)).onNext(stateCaptor.capture(), eventCaptor.capture())
        val capturedInputStates = stateCaptor.allValues
        val expectedInputStates = listOf(null, StateType(0), StateType(1))
        assertEquals(expectedInputStates, capturedInputStates)
        val capturedInputEventRecords = eventCaptor.allValues
        assertEquals(inputEventRecords, capturedInputEventRecords)
        assertEquals(task, result.processorTask)
        assertEquals(listOf(0, 1, 2).map { EventType("outputEvent$it").toRecord() }, result.outputEvents)
        assertNotNull(result.updatedState)
    }
}