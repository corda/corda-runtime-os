package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.libs.statemanager.api.State
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.mediator.taskmanager.TaskType
import net.corda.messaging.api.processor.StateAndEventProcessor
import java.time.Instant

/**
 * Helper that creates and executes various tasks used by [MultiSourceEventMediatorImpl].
 */
internal class TaskManagerHelper<K : Any, S : Any, E : Any>(
    private val taskManager: TaskManager,
    private val stateManager: StateManagerHelper<K, S, E>,
) {

    /**
     * Creates [ProcessorTask]s for given events and states.
     *
     * @param messageGroups Map of messages keys and related events.
     * @param persistedStates Mpa of message keys and related states.
     * @param messageProcessor State and event processor.
     * @return Created [ProcessorTask]s.
     */
    fun createMsgProcessorTasks(
        messageGroups: Map<K, List<CordaConsumerRecord<K, E>>>,
        persistedStates: Map<String, State>,
        messageProcessor:StateAndEventProcessor<K, S, E>,
    ): List<ProcessorTask<K, S, E>> {
        return messageGroups.map { msgGroup ->
            val key = msgGroup.key.toString()
            val events = msgGroup.value.map { it }
            ProcessorTask(
                key,
                persistedStates[key],
                events,
                messageProcessor,
                stateManager,
            )
        }
    }

    /**
     * Creates [ProcessorTask]s from [ClientTask.Result]s that have reply message set. Reply messages are
     * grouped by message keys. The latest updated state from related [ProcessorTask] is used as the input state to
     * state and event processor.
     *
     * @param clientResults List of results of [ClientTask]s.
     * @return Created [ProcessorTask]s.
     */
    fun createMsgProcessorTasks(
        clientResults: List<ClientTask.Result<K, S, E>>,
    ): List<ProcessorTask<K, S, E>> {
        return clientResults.filter { it.hasReply() }
            .groupBy { it.clientTask.processorTask.persistedState!!.key }
            .map { (_, clientTaskResults) ->
                val messageGroup = clientTaskResults.map { it.toCordaConsumerRecord() }
                clientTaskResults.first().clientTask.processorTask.copy(events = messageGroup)
            }
    }

    /**
     * Creates [ProcessorTask]s from [ProcessorTask.Result]s of [ProcessorTask] that failed to update state via
     * [StateManager] due to conflicts.
     *
     * @param invalidResults [ProcessorTask.Result]s of [ProcessorTask] that failed to update state via
     *   [StateManager] due to conflicts.
     * @param persistedStates The latest states from [StateManager].
     * @return Created [ProcessorTask]s.
     */
    fun createMsgProcessorTasks(
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
    fun createProducerTasks(
        processorTaskResults: List<ProcessorTask.Result<K, S, E>>,
        messageRouter: MessageRouter,
    ): List<ClientTask<K, S, E>> {
        return processorTaskResults.map { result ->
            result.outputEvents.map { event ->
                val message = MediatorMessage(event.value!!)
                ClientTask(
                    message,
                    messageRouter,
                    result.processorTask
                )
            }
        }.flatten()
    }

    /**
     * Executes given [ClientTask]s using [TaskManager] and waits for all to finish.
     *
     * @param clientTasks Tasks to execute.
     * @return Result of task executions.
     */
    fun executeProducerTasks(
        clientTasks: Collection<ClientTask<K, S, E>>
    ): List<ClientTask.Result<K, S, E>> {
//        return producerTasks.map { producerTask ->
//            taskManager.execute(TaskType.SHORT_RUNNING, producerTask::call)
//        }.map {
//            it.join()
//        }
        return runBlocking {
            clientTasks.map { it.call() }
        }
    }

    /**
     * Converts [ClientTask.Result] to [CordaConsumerRecord].
     */
    private fun ClientTask.Result<K, S, E>.toCordaConsumerRecord() =
        (CordaConsumerRecord(
        "",
        -1,
        -1,
        clientTask.processorTask.events.first().key,
        replyMessage!!.payload,
        Instant.now().toEpochMilli()
    ))
}