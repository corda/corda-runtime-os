package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.mediator.taskmanager.TaskType
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.utils.toRecord

/**
 * Helper that creates and executes various tasks used by [MultiSourceEventMediatorImpl].
 */
internal class TaskManagerHelper<K : Any, S : Any, E : Any>(
    private val taskManager: TaskManager,
    private val stateManagerHelper: StateManagerHelper<K, S, E>,
) {

    /** Same as [Callable] but with suspend function call. */
    fun interface SuspendCallable<V> {
        @Throws(Exception::class)
        suspend fun call(): V
    }

    /**
     * Creates [ProcessorTask]s for given events and states.
     *
     * @param messageGroups Map of messages keys and related events.
     * @param persistedStates Mpa of message keys and related states.
     * @param messageProcessor State and event processor.
     * @return Created [ProcessorTask]s.
     */
    fun createMessageProcessorTasks(
        messageGroups: Map<K, List<CordaConsumerRecord<K, E>>>,
        persistedStates: Map<String, State>,
        messageProcessor: StateAndEventProcessor<K, S, E>,
    ): List<ProcessorTask<K, S, E>> {
        return messageGroups.map { msgGroup ->
            val key = msgGroup.key.toString()
            val events = msgGroup.value.map { it.toRecord() }
            ProcessorTask(
                key,
                persistedStates[key],
                events,
                messageProcessor,
                stateManagerHelper,
            )
        }
    }

    /**
     * Creates [ProcessorTask]s from [ClientTask.Result]s that have reply message set. Reply messages are grouped by
     * message keys. The latest updated state from related [ProcessorTask], and reply from the messaging client are used
     * as the inputs to state and event processor.
     *
     * @param clientResults List of results of [ClientTask]s.
     * @return Created [ProcessorTask]s.
     */
    fun createMessageProcessorTasks(
        clientResults: List<ClientTask.Result<K, S, E>>,
    ): List<ProcessorTask<K, S, E>> {
        return clientResults.filter { it.hasReply }
            .groupBy { it.key }
            .map { (_, clientTaskResults) ->
                val groupedEvents = clientTaskResults.map { it.toRecord() }
                with(clientTaskResults.first()) {
                    processorTask.copy(
                        persistedState = processorTaskResult.updatedState,
                        events = groupedEvents
                    )
                }
            }
    }

    /**
     * Creates [ProcessorTask]s from failed [ProcessorTask.Result]s of [ProcessorTask] that failed to update state via
     * [StateManager] due to conflicts.
     *
     * @param invalidResults [ProcessorTask.Result]s of [ProcessorTask] that failed to update state via
     *   [StateManager] due to conflicts.
     * @param persistedStates The latest states from [StateManager].
     * @return Created [ProcessorTask]s.
     */
    fun createMessageProcessorTasks(
        invalidResults: List<ProcessorTask.Result<K, S, E>>,
        persistedStates: Map<String, State?>,
    ): List<ProcessorTask<K, S, E>> {
        return invalidResults.map {
            it.processorTask.copy(persistedState = persistedStates[it.processorTask.key])
        }
    }

    /**
     * Executes given [ProcessorTask]s using [TaskManager] and waits for all to finish.
     *
     * @param processorTasks Tasks to execute.
     * @return Result of task executions.
     */
    fun executeProcessorTasks(
        processorTasks: Collection<ProcessorTask<K, S, E>>
    ): List<ProcessorTask.Result<K, S, E>> {
        return processorTasks.map { processorTask ->
            taskManager.execute(TaskType.SHORT_RUNNING, processorTask::call)
        }.map {
            it.join()
        }
    }

    /**
     * Creates [ClientTask]s for given results of [ProcessorTask]s. Given [MessageRouter] is used to select messaging
     * client for specific message.
     *
     * @param processorTaskResults Results of [ClientTask]s.
     * @param messageRouter Message router.
     * @return Created [ClientTask]s.
     */
    fun createClientTasks(
        processorTaskResults: List<ProcessorTask.Result<K, S, E>>,
        messageRouter: MessageRouter,
    ): List<ClientTask<K, S, E>> {
        return processorTaskResults.map { result ->
            result.outputEvents.map { event ->
                val message = MediatorMessage(event.value!!)
                ClientTask(
                    message,
                    messageRouter,
                    result,
                )
            }
        }.flatten()
    }

    /**
     * Executes given [ClientTask]s and waits for all to finish.
     *
     * @param clientTasks Tasks to execute.
     * @return Result of task executions.
     */
    fun executeClientTasks(
        clientTasks: Collection<ClientTask<K, S, E>>
    ): List<ClientTask.Result<K, S, E>> {
        return runBlocking {
            clientTasks.map { it.call() }
        }
    }

    /**
     * Converts [ClientTask.Result] to [Record].
     */
    private fun ClientTask.Result<K, S, E>.toRecord() =
        Record(
            "",
            processorTask.events.first().key,
            replyMessage!!.payload,
        )
}