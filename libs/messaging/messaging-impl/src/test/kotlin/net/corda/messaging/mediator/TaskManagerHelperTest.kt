package net.corda.messaging.mediator

import net.corda.libs.statemanager.api.State
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_KEY
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import net.corda.messaging.api.records.Record
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class TaskManagerHelperTest {
    private companion object {
        const val KEY1 = "key1"
        const val KEY2 = "key2"
        const val EVENT1 = "event1"
        const val EVENT2 = "event2"
        const val EVENT3 = "event3"
    }

    private val taskManager = mock<TaskManager>()
    private val stateManagerHelper = mock<StateManagerHelper<String, String, String>>()
    private val taskManagerHelper = TaskManagerHelper(taskManager, stateManagerHelper)
    private val messageProcessor = mock<StateAndEventProcessor<String, String, String>>()

    @Test
    fun `successfully creates message processor tasks from states and events`() {
        val messageGroups = mapOf(
            KEY1 to listOf(EVENT1).toCordaConsumerRecords(KEY1),
            KEY2 to listOf(EVENT2, EVENT3).toCordaConsumerRecords(KEY2),
        )
        val state1 = mock<State>()
        val persistedStates = mapOf(
            KEY1 to state1
        )

        val processorTasks = taskManagerHelper.createMessageProcessorTasks(
            messageGroups, persistedStates, messageProcessor
        )

        val expectedProcessorTasks = listOf(
            ProcessorTask(
                KEY1,
                state1,
                listOf(EVENT1).toRecords(KEY1),
                messageProcessor,
                stateManagerHelper,
            ),
            ProcessorTask(
                KEY2,
                null,
                listOf(EVENT2, EVENT3).toRecords(KEY2),
                messageProcessor,
                stateManagerHelper,
            ),
        )
        assertEquals(expectedProcessorTasks, processorTasks)
    }

    @Test
    fun `successfully creates message processor tasks from client tasks results`() {
        val updateState = State(
            KEY2, ByteArray(0), version = 1, mock(), mock()
        )

        fun clientTaskResult(
            key: String,
            events: List<String>,
            replyMessage: MediatorMessage<String>?,
        ): ClientTask.Result<String, String, String> {
            val processorTask = ProcessorTask(
                key, null, events.toRecords(key), messageProcessor, stateManagerHelper
            )
            val processorTaskResult = ProcessorTask.Result(
                processorTask, listOf(), updateState
            )
            val clientTask = ClientTask(
                mock(), mock(), processorTaskResult
            )
            return ClientTask.Result(
                clientTask, replyMessage
            )
        }

        val replyMessage = MediatorMessage("replyMessage")
        val clientResults = listOf(
            clientTaskResult(KEY1, listOf(EVENT1), replyMessage = null),
            clientTaskResult(KEY2, listOf(EVENT2, EVENT3), replyMessage),
        )

        val processorTasks = taskManagerHelper.createMessageProcessorTasks(
            clientResults,
        )

        val expectedProcessorTasks = listOf(
            ProcessorTask(
                KEY2,
                updateState.copy(version = updateState.version + 1),
                listOf(replyMessage.payload!!).toRecords(KEY2),
                messageProcessor,
                stateManagerHelper
            ),
        )
        assertEquals(expectedProcessorTasks, processorTasks)
    }

    @Test
    fun `successfully creates message processor tasks from task that failed to update state`() {
        val processorTask1 = ProcessorTask(
            KEY1,
            persistedState = mock(),
            events = mock(),
            messageProcessor,
            stateManagerHelper
        )
        val processorTask2 = ProcessorTask(
            KEY2,
            persistedState = mock(),
            events = mock(),
            messageProcessor,
            stateManagerHelper
        )
        val failedResults = listOf(
            ProcessorTask.Result(
                processorTask1,
                outputEvents = mock(),
                updatedState = mock()
            ),
            ProcessorTask.Result(
                processorTask2,
                outputEvents = mock(),
                updatedState = mock()
            ),
        )
        val state1 = mock<State>()
        val state2 = mock<State>()
        val persistedStates = mapOf<String, State?>(
            KEY1 to state1,
            KEY2 to state2,
        )

        val processorTasks = taskManagerHelper.createMessageProcessorTasks(
            failedResults,
            persistedStates,
        )

        val expectedProcessorTasks = listOf(
            processorTask1.copy(persistedState = state1),
            processorTask2.copy(persistedState = state2),
        )
        assertEquals(expectedProcessorTasks, processorTasks)
    }

    @Test
    fun `successfully executes processor tasks`() {
        val processorTask1 = mock<ProcessorTask<String, String, String>>()
        val processorTask2 = mock<ProcessorTask<String, String, String>>()

        `when`(taskManager.execute(any(), any<() -> ProcessorTask.Result<String, String, String>>())).thenReturn(mock())

        taskManagerHelper.executeProcessorTasks(
            listOf(processorTask1, processorTask2)
        )

        val commandCaptor = argumentCaptor<() -> ProcessorTask.Result<String, String, String>>()
        verify(taskManager, times(2)).execute(any(), commandCaptor.capture())
        assertEquals(processorTask1::call, commandCaptor.firstValue)
        assertEquals(processorTask2::call, commandCaptor.secondValue)
    }

    @Test
    fun `successfully creates client tasks from message processor tasks`() {
        val processorTaskResult1 = ProcessorTask.Result(
            mock<ProcessorTask<String, String, String>>(),
            outputEvents = listOf(EVENT1).toRecords(KEY1),
            mock<State>(),
        )
        val processorTaskResult2 = ProcessorTask.Result(
            mock<ProcessorTask<String, String, String>>(),
            outputEvents = listOf(EVENT2, EVENT3).toRecords(KEY2),
            mock<State>(),
        )
        val messageRouter = mock<MessageRouter>()

        val clientTasks = taskManagerHelper.createClientTasks(
            listOf(processorTaskResult1, processorTaskResult2),
            messageRouter,
        )

        val expectedClientTasks = listOf(
            ClientTask(
                MediatorMessage(EVENT1, mutableMapOf(MSG_PROP_KEY to KEY1)),
                messageRouter,
                processorTaskResult1,
            ),
            ClientTask(
                MediatorMessage(EVENT2, mutableMapOf(MSG_PROP_KEY to KEY2)),
                messageRouter,
                processorTaskResult2,
            ),
            ClientTask(
                MediatorMessage(EVENT3, mutableMapOf(MSG_PROP_KEY to KEY2)),
                messageRouter,
                processorTaskResult2,
            ),
        )
        assertEquals(expectedClientTasks, clientTasks)
    }

    @Test
    fun `successfully executes client tasks`() {
        val clientTask1 = mock<ClientTask<String, String, String>>()
        val clientTask2 = mock<ClientTask<String, String, String>>()

        `when`(taskManager.execute(any(), any<() -> ClientTask.Result<String, String, String>>())).thenReturn(mock())

        taskManagerHelper.executeClientTasks(
            listOf(clientTask1, clientTask2)
        )

        val commandCaptor = argumentCaptor<() -> ClientTask.Result<String, String, String>>()
        verify(taskManager, times(2)).execute(any(), commandCaptor.capture())
        assertEquals(clientTask1::call, commandCaptor.firstValue)
        assertEquals(clientTask2::call, commandCaptor.secondValue)
    }

    private fun List<String>.toCordaConsumerRecords(key: String) =
        this.map {
            CordaConsumerRecord(
                topic = "", partition = 0, offset = 0, key, it, timestamp = 0
            )
        }

    private fun List<String>.toRecords(key: String) =
        this.map {
            Record(
                topic = "", key, it, timestamp = 0
            )
        }
}
