package net.corda.session.mapper.service.executor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.Metadata
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.State
import net.corda.messaging.api.records.FLOW_CREATED_TIMESTAMP_RECORD_HEADER
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.schema.configuration.FlowConfig
import net.corda.session.mapper.service.state.StateMetadataKeys.FLOW_MAPPER_STATUS
import net.corda.tracing.traceStateAndEventExecution
import net.corda.utilities.debug
import net.corda.utilities.time.UTCClock
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * The [FlowMapperMessageProcessor] receives states and events that are keyed by strings. These strings can be either:
 *
 * - `toString`ed [FlowKey]s representing flow starts.
 * - Session ids representing sessions.
 */
class FlowMapperMessageProcessor(
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    private val flowConfig: SmartConfig,
    ) : StateAndEventProcessor<String, FlowMapperState, FlowMapperEvent> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val sessionP2PTtl = flowConfig.getLong(FlowConfig.SESSION_P2P_TTL)

    private val clock = UTCClock()

    override fun onNext(
        state: State<FlowMapperState>?,
        event: Record<String, FlowMapperEvent>,
    ): StateAndEventProcessor.Response<FlowMapperState> {

        val key = event.key
        logger.trace { "Received event. Key: $key Event: ${event.value}" }
        val value = event.value ?: return StateAndEventProcessor.Response(state, emptyList())
        val eventType = value.payload?.javaClass?.simpleName ?: "Unknown"

        val flowCreatedTimeStampHeader =
            event.headers.mapNotNull { if (it.first == FLOW_CREATED_TIMESTAMP_RECORD_HEADER) it.second else null }
                .firstOrNull()?.let { FLOW_CREATED_TIMESTAMP_RECORD_HEADER to it }

        CordaMetrics.Metric.FlowMapperEventLag.builder()
            .withTag(CordaMetrics.Tag.FlowEvent, value.payload::class.java.name)
            .build().record(Duration.ofMillis(clock.instant().toEpochMilli() - event.timestamp))
        val eventProcessingTimer = CordaMetrics.Metric.FlowMapperEventProcessingTime.builder()
            .withTag(CordaMetrics.Tag.FlowEvent, value.payload::class.java.name)
            .build()
        return traceStateAndEventExecution(event, "Flow Mapper Event - $eventType") {
            eventProcessingTimer.recordCallable {
                if (!isExpiredSessionEvent(value)) {
                    val executor = flowMapperEventExecutorFactory.create(key, value, state?.value, flowConfig)
                    val result = executor.execute()
                    val newMap = result.flowMapperState?.status?.let {
                        mapOf(FLOW_MAPPER_STATUS to it.toString())
                    } ?: mapOf()
                    StateAndEventProcessor.Response(
                        State(result.flowMapperState, state?.metadata?.let {
                            Metadata(it + newMap)
                        } ?: Metadata(newMap)),
                        result.outputEvents
                    )
                } else {
                    logger.debug { "This event is expired and will be ignored. Event: $event State: $state" }
                    CordaMetrics.Metric.FlowMapperExpiredSessionEventCount.builder()
                        .build().increment()
                    StateAndEventProcessor.Response(state, emptyList())
                }
            }!!
        }.let {
            if (flowCreatedTimeStampHeader != null) {
                it.copy(responseEvents = it.responseEvents.map {
                    it.copy(headers = it.headers + flowCreatedTimeStampHeader)
                })
            } else it
        }
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
