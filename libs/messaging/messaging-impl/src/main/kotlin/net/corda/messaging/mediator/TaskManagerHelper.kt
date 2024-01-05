package net.corda.messaging.mediator

import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_KEY
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_TOPIC
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.messaging.utils.toRecord
import net.corda.taskmanager.TaskManager

/**
 * Helper that creates and executes various tasks used by [MultiSourceEventMediatorImpl].
 */
internal class TaskManagerHelper<K : Any, S : Any, E : Any>(
    private val taskManager: TaskManager,
    private val stateManagerHelper: StateManagerHelper<K, S, E>,
    private val metrics: EventMediatorMetrics,
) {

    /**
     * Creates [ProcessorTask]s for given events and states.
     *
     * @param messageGroups Map of messages keys and related events.
     * @param persistedStates Map of message keys and related states.
     * @param messageProcessor State and event processor.
     * @return Created [ProcessorTask]s.
     */
    fun createMessageProcessorTasks(
        messageGroups: Map<K, List<CordaConsumerRecord<K, E>>>,
        persistedStates: Map<String, State>,
        messageProcessor: StateAndEventProcessor<K, S, E>,
    ): List<ProcessorTask<K, S, E>> {
        return messageGroups.map { msgGroup ->
            val key = msgGroup.key
            val events = msgGroup.value.map { it.toRecord() }
            ProcessorTask(
                key,
                persistedStates[key.toString()],
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
                    val persistedState = processorTaskResult.updatedState!!
                    val incrementVersion = if (processorTaskResult.processorTask.persistedState == null) 0 else 1
                    processorTask.copy(
                        persistedState = persistedState.copy(version = persistedState.version + incrementVersion),
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
            it.processorTask.copy(persistedState = persistedStates[it.processorTask.key.toString()])
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
        return metrics.processorTimer.recordCallable {
            processorTasks.map { processorTask ->
                taskManager.executeShortRunningTask(processorTask::call)
            }.map {
                it.join()
            }
        }!!
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
    ): Map<K, List<ClientTask<K, S, E>>> {
        return processorTaskResults.associate { result ->
            result.key to result.outputEvents.map { event ->
                ClientTask(
                    event.toMessage(),
                    messageRouter,
                    result,
                )
            }
        }
    }

    /**
     * Executes given [ClientTask]s and waits for all to finish.
     *
     * @param clientTaskMap Tasks to execute.
     * @return Result of task executions.
     */
    fun executeClientTasks(
        clientTaskMap: Map<K, List<ClientTask<K, S, E>>>
    ): List<ClientTask.Result<K, S, E>> {
        return clientTaskMap.map { (_, clientTasks) ->
            taskManager.executeShortRunningTask {
                clientTasks.map { it.call() }
            }
        }.flatMap {
            it.join()
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

    /**
     * Converts [Record] to [MediatorMessage].
     */
    private fun Record<*, *>.toMessage() =
        MediatorMessage(
            value!!,
            headers.toMessageProperties().also { it[MSG_PROP_KEY] = key },
        )

    private fun List<Pair<String, String>>.toMessageProperties() =
        associateTo(mutableMapOf()) { (key, value) -> key to (value as Any) }
}