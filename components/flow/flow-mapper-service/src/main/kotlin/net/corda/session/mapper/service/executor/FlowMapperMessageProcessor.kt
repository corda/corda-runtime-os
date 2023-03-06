package net.corda.session.mapper.service.executor

import java.time.Instant
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

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
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val sessionP2PTtl = flowConfig.getLong(FlowConfig.SESSION_P2P_TTL)

    override fun onNext(
        state: FlowMapperState?,
        event: Record<String, FlowMapperEvent>
    ): StateAndEventProcessor.Response<FlowMapperState> {
        val key = event.key
        logger.trace { "Received event: key: $key event: ${event.value}" }
        val value = event.value ?: return StateAndEventProcessor.Response(state, emptyList())

        return if (!isExpiredSessionEvent(value) && isValidState(state, value)) {
            val executor = flowMapperEventExecutorFactory.create(key, value, state, flowConfig)
            val result = executor.execute()
            StateAndEventProcessor.Response(result.flowMapperState, result.outputEvents)
        } else {
            logger.debug { "Ignoring event (isExpiredSessionEvent: ${isExpiredSessionEvent(value)}, " +
                    "isValidState: ${isValidState(state, value)})" }
            StateAndEventProcessor.Response(state, emptyList())
        }
    }

    /**
     * Only allow events to be processed when one of the following criteria is met:
     * - the messages are for a new state.
     * - the state is set to [FlowMapperStateType.OPEN],
     * - it is a cleanup event
     * @param state the current state for this mapper event
     * @param mapperEvent the mapper event
     * @return true if mapper state is valid for processing flow events or if it is a cleanup event. False otherwise.
     * a [SessionEvent]
     */
    private fun isValidState(state: FlowMapperState?, mapperEvent: FlowMapperEvent): Boolean {
        return state == null || state.status == FlowMapperStateType.OPEN
                || mapperEvent.payload is ExecuteCleanup || mapperEvent.payload is ScheduleCleanup
    }

    /**
     * Returns true if the [event] is a [SessionEvent] that is expired.
     * An expired event is one whose timestamp + configured Flow P2P TTL value is a point of time in the past
     * @param event Any flow mapper event
     * @return True if it is an expired [SessionEvent], false otherwise
     */
    private fun isExpiredSessionEvent(event: FlowMapperEvent): Boolean {
        val payload = event.payload
        if (payload is SessionEvent) {
            val sessionEventExpiryTime = payload.timestamp.toEpochMilli() + sessionP2PTtl
            val currentTime = Instant.now().toEpochMilli()
            if (currentTime > sessionEventExpiryTime) {
                return true
            }
        }

        return false
    }

    override val keyClass = String::class.java
    override val stateValueClass = FlowMapperState::class.java
    override val eventValueClass = FlowMapperEvent::class.java
}
