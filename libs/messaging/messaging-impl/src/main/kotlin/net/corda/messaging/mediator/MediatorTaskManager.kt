package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.statemanager.State
import net.corda.messaging.api.mediator.statemanager.StateManager
import net.corda.messaging.api.mediator.taskmanager.TaskManager
import net.corda.messaging.api.mediator.taskmanager.TaskType
import net.corda.messaging.api.processor.StateAndEventProcessor
import java.time.Instant

/**
 * Helper that creates and executes various tasks used by [MultiSourceEventMediatorImpl].
 */
internal class MediatorTaskManager<K : Any, S : Any, E : Any>(
    private val taskManager: TaskManager,
    private val stateManager: MediatorStateManager<K, S, E>,
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
     * Creates [ProcessorTask]s from [ProducerTask.Result]s that have reply message set. Reply messages are
     * grouped by message keys. The latest updated state from related [ProcessorTask] is used as the input state to
     * state and event processor.
     *
     * @param producerResults List of results of [ProducerTask]s.
     * @return Created [ProcessorTask]s.
     */
    fun createMsgProcessorTasks(
        producerResults: List<ProducerTask.Result<K, S, E>>,
    ): List<ProcessorTask<K, S, E>> {
        return producerResults.filter { it.hasReply() }
            .groupBy { it.producerTask.processorTask.persistedState!!.key }
            .map { (_, producerTaskResults) ->
                val messageGroup = producerTaskResults.map { it.toCordaConsumerRecord() }
                producerTaskResults.first().producerTask.processorTask.copy(events = messageGroup)
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
        persistedStates: Map<String, State>,
    ): List<ProcessorTask<K, S, E>> {
        return invalidResults.map {
            it.processorTask.copy(persistedState = persistedStates[it.processorTask.key]!!)
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
     * Creates [ProducerTask]s for given results of [ProcessorTask]s. Given [MessageRouter] is used to select producer
     * for specific message.
     *
     * @param processorTaskResults Results of [ProducerTask]s.
     * @param messageRouter Message router.
     * @return Created [ProducerTask]s.
     */
    fun createProducerTasks(
        processorTaskResults: List<ProcessorTask.Result<K, S, E>>,
        messageRouter: MessageRouter,
    ): List<ProducerTask<K, S, E>> {
        return processorTaskResults.map { result ->
            result.outputEvents.map { event ->
                val message = MediatorMessage(event.value!!)
                ProducerTask(
                    message,
                    messageRouter,
                    result.processorTask
                )
            }
        }.flatten()
    }

    /**
     * Executes given [ProducerTask]s using [TaskManager] and waits for all to finish.
     *
     * @param producerTasks Tasks to execute.
     * @return Result of task executions.
     */
    fun executeProducerTasks(
        producerTasks: Collection<ProducerTask<K, S, E>>
    ): List<ProducerTask.Result<K, S, E>> {
//        return producerTasks.map { producerTask ->
//            taskManager.execute(TaskType.SHORT_RUNNING, producerTask::call)
//        }.map {
//            it.join()
//        }
        return runBlocking {
            producerTasks.map { it.call() }
        }
    }

    /**
     * Converts [ProducerTask.Result] to [CordaConsumerRecord].
     */
    private fun ProducerTask.Result<K, S, E>.toCordaConsumerRecord() =
        (CordaConsumerRecord(
        "",
        -1,
        -1,
        producerTask.processorTask.events.first().key,
        replyMessage!!.payload,
        Instant.now().toEpochMilli()
    ))
}