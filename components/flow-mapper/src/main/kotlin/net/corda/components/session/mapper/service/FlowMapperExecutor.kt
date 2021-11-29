package net.corda.components.session.mapper.service

import net.corda.components.session.mapper.ScheduledTaskState
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class FlowMapperExecutor(
    private val scheduledTaskState: ScheduledTaskState,
    private val flowMapperTopic: String,
    private val flowEventTopic: String,
    private val p2pOutTopic: String
) :
    StateAndEventProcessor<String, FlowMapperState, FlowEvent> {

    companion object {
        private val log = contextLogger()
    }

    private val executorService: ScheduledExecutorService = scheduledTaskState.executorService
    private val scheduledTasks: MutableMap<String, ScheduledFuture<*>> = scheduledTaskState.tasks
    private val publisher: Publisher = scheduledTaskState.publisher

    override fun onNext(state: FlowMapperState?, event: Record<String, FlowEvent>): StateAndEventProcessor
    .Response<FlowMapperState> {
        log.debug { "FlowMapperProcessor processor: $event" }
        val message = event.value ?: return StateAndEventProcessor.Response(state, emptyList())

        return when (val flowEvent = message.payload) {
            is SessionEvent -> {
                val (updatedState, outputEvents) = processSessionEvent(flowEvent, state, message, event.key)
                StateAndEventProcessor.Response(updatedState, outputEvents)
            }
            is StartRPCFlow -> {
                val (updatedState, outputEvents) = processStartRPCFlow(flowEvent, state, event.key)
                StateAndEventProcessor.Response(updatedState, outputEvents)
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    private fun processStartRPCFlow(
        flowEvent: StartRPCFlow,
        state: FlowMapperState?,
        key: String
    ): Pair<FlowMapperState?, MutableList<Record<*, *>>> {
        return if (state != null) {
            val flowKey = generateFlowEventKey(flowEvent.rpcUsername, generateFlowId())
            val updatedState = FlowMapperState(
                null,
                null,
                flowKey,
                null,
                FlowMapperStateType.OPEN
            )

            //highlighting here that in this model we can save this key to the checkpoint in the flow engine
            //then we can trigger a cleanup event for this key when the flow ends in the flow engine.
            flowEvent.startRPCFlowKey = key

            Pair(updatedState, mutableListOf(Record(flowEventTopic, flowKey, flowEvent)))
        } else {
            //duplicate
            Pair(state, mutableListOf())
        }
    }

    private fun processSessionEvent(
        sessionEvent: SessionEvent,
        state: FlowMapperState?,
        message: FlowEvent,
        key: String
    ): Pair<FlowMapperState?, MutableList<Record<*, *>>> {
        var flowMapperState = state
        val outputEvents = mutableListOf<Record<*, *>>()
        val messageDirection = sessionEvent.messageDirection
        val sessionEventPayload = sessionEvent.payload

        val outputTopic = getOutputTopic(messageDirection)

        when (sessionEventPayload) {
            is SessionInit -> {
                if (state == null) {
                    val (ourSessionId, counterpartySessionId, flowKey, outputRecordKey, outputRecordValue) =
                        getSessionInitState(messageDirection, sessionEventPayload, sessionEvent.initiatedIdentity)

                    flowMapperState = FlowMapperState(
                        ourSessionId,
                        counterpartySessionId,
                        flowKey,
                        null,
                        FlowMapperStateType.OPEN
                    )
                    outputEvents.add(Record(outputTopic, outputRecordKey, outputRecordValue))
                } else {
                    //duplicate
                }
            }
            is ScheduleCleanup -> {
                if (state == null) {
                    //invalid session.
                } else {
                    setupCleanupTimer(key, sessionEventPayload)
                    state.status = FlowMapperStateType.CLOSING
                    flowMapperState = state
                }
            }
            is ExecuteCleanup -> {
                flowMapperState = null
            }
            else -> {
                if (state == null || state.status != FlowMapperStateType.OPEN) {
                    //invalid session.
                } else {
                    val recordKey = getRecordKey(state, messageDirection)
                    outputEvents.add(Record(outputTopic, recordKey, message))
                }
            }
        }
        return Pair(flowMapperState, outputEvents)
    }

    /**
     * Set up a cleanup timer for this key
     */
    private fun setupCleanupTimer(eventKey: String, sessionEventPayload: ScheduleCleanup) {
        val scheduleTask = executorService.schedule(
            {
                log.debug { "Clearing up mapper state for key $eventKey" }
                publisher.publish(listOf(Record(flowMapperTopic, eventKey, ScheduleCleanup())))
            },
            sessionEventPayload.expiryTime - System.currentTimeMillis(),
            TimeUnit.MILLISECONDS
        )
        scheduledTasks[eventKey] = scheduleTask
    }

    /**
     * Get a helper object to obtain:
     * - our sessionId
     * - counterparty sessionId
     * - output record key
     * - output record value
     */
    private fun getSessionInitState(
        messageDirection: MessageDirection?,
        sessionInit: SessionInit,
        initiatedIdentity: HoldingIdentity
    ): SessionInitState {
        return when (messageDirection) {
            MessageDirection.INBOUND -> {
                val flowKey = generateFlowEventKey(initiatedIdentity, generateFlowId())
                SessionInitState(
                    sessionInit.initiatedSessionID,
                    sessionInit.initiatingSessionID,
                    flowKey,
                    flowKey,
                    sessionInit)
            }
            MessageDirection.OUTBOUND -> {
                //reusing SessionInit object for inbound and outbound traffic rather than creating a new object identical to SessionInit
                //with an extra field of flowKey. set flowkey to null to not expose it on outbound messages
                sessionInit.flowKey = null
                SessionInitState(
                    sessionInit.initiatingSessionID,
                    sessionInit.initiatedSessionID,
                    sessionInit.flowKey,
                    sessionInit.initiatedSessionID,
                    sessionInit
                )
            }
            else -> {
                throw IllegalArgumentException("TODO replace with new exceptions")
            }
        }
    }

    /**
     * Get the output record key based on [messageDirection] from the [state].
     * Inbound records should use the flowKey.
     * Outbound records should use the counterparty sessionId.
     */
    private fun getRecordKey(state: FlowMapperState, messageDirection: MessageDirection?): String {
        return when (messageDirection) {
            MessageDirection.INBOUND -> {
                state.flowEventKey
            }
            MessageDirection.OUTBOUND -> {
                state.counterpartySessionId
            }
            else -> {
                throw IllegalArgumentException("TODO replace with new exceptions")
            }
        }
    }

    /**
     * Get the output topic based on [messageDirection].
     * Inbound records should be directed to the flow event topic.
     * Outbound records should be directed to the p2p out topic.
     */
    private fun getOutputTopic(messageDirection: MessageDirection): String {
        return when (messageDirection) {
            MessageDirection.INBOUND -> {
                flowEventTopic
            }
            MessageDirection.OUTBOUND -> {
                p2pOutTopic
            }
            else -> {
                throw IllegalArgumentException("TODO replace with new exceptions")
            }
        }

    }

    /**
     * Random ID for flowId
     */
    private fun generateFlowId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Include identity in the flowKey
     */
    private fun generateFlowEventKey(initiatedIdentity: HoldingIdentity, flowId: String): String {
        return initiatedIdentity.x500Name + initiatedIdentity.groupId + flowId
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<FlowMapperState>
        get() = FlowMapperState::class.java
    override val eventValueClass: Class<FlowEvent>
        get() = FlowEvent::class.java

    /**
     * Helper class
     */
    data class SessionInitState(
        val sessionId: String,
        val counterpartySessionId: String,
        val flowKey: String,
        val outputRecordKey: String,
        val outputRecordValue: SessionInit
    )

}
