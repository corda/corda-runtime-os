package net.corda.session.mapper.service.executor

import java.time.Instant
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace

/**
 * The [FlowMapperMessageProcessor] receives states and events that are keyed by strings. These strings can be either:
 *
 * - `toString`ed [FlowKey]s representing flow starts.
 * - Session ids representing sessions.
 */
class FlowMapperMessageProcessor(
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    private val flowConfig: SmartConfig
) : StateAndEventProcessor<String, FlowMapperState, FlowMapperEvent> {

    companion object {
        private val logger = contextLogger()
    }

    private val sessionP2PTtl = flowConfig.getLong(FlowConfig.SESSION_P2P_TTL)

    override fun onNext(
        state: FlowMapperState?,
        event: Record<String, FlowMapperEvent>
    ): StateAndEventProcessor.Response<FlowMapperState> {
        val key = event.key
        logger.trace { "Received event: key: $key event: ${event.value}" }
        val value = event.value ?: return StateAndEventProcessor.Response(state, emptyList())

        return if (isExpiredSessionEvent(value) || !isValidState(state, value)) {
            StateAndEventProcessor.Response(state, emptyList())
        } else {
            val executor = flowMapperEventExecutorFactory.create(key, value, state, flowConfig)
            val result = executor.execute()
            StateAndEventProcessor.Response(result.flowMapperState, result.outputEvents)
        }
    }

    /**
     * Only allow flow events to be processed when:
     * - the messages are for a new state.
     * - if it is an existing state that is set to [FlowMapperStateType.OPEN],
     * - it is not a [FlowEvent]
     * @param state the current state for this mapper event
     * @param mapperEvent the mapper event
     * @return true if mapper state is valid for flow event processing. False if the state is not valid for a flow event or if it is not
     * a [FlowEvent]
     */
    private fun isValidState(state: FlowMapperState?, mapperEvent: FlowMapperEvent): Boolean {
        return state == null || state.status == FlowMapperStateType.OPEN || mapperEvent.payload !is FlowEvent
    }

    /**
     * Returns true if the [event] is a [SessionEvent] that is expired.
     * An expired event is one whose timestamp + configured Flow P2P TTL value is a point of time in the past
     * @param event Any flow mapper event
     * @return True if it is an expired [SessionEvent], false otherwise
     */
    private fun isExpiredSessionEvent(event: FlowMapperEvent): Boolean {
        val payload = event.payload
        if (payload is FlowEvent) {
            val flowEventPayload = payload.payload
            if (flowEventPayload is SessionEvent) {
                val sessionEventExpiryTime = flowEventPayload.timestamp.toEpochMilli() + sessionP2PTtl
                val currentTime = Instant.now().toEpochMilli()
                if (currentTime > sessionEventExpiryTime) {
                    return true
                }
            }
        }

        return false
    }

    override val keyClass = String::class.java
    override val stateValueClass = FlowMapperState::class.java
    override val eventValueClass = FlowMapperEvent::class.java
}
