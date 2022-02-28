package net.corda.flow.mapper.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.StartFlowExecutor
import net.corda.flow.mapper.impl.executor.SessionInitExecutor
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [FlowMapperEventExecutorFactory::class])
class FlowMapperEventExecutorFactoryImpl : FlowMapperEventExecutorFactory {

    override fun create(eventKey: String, flowMapperEvent: FlowMapperEvent, state: FlowMapperState?, instant: Instant):
            FlowMapperEventExecutor {
        return when (val sessionEvent = flowMapperEvent.payload) {
            is SessionEvent -> {
                val eventPayload = sessionEvent.payload
                if (eventPayload is SessionInit) {
                    SessionInitExecutor(eventKey, sessionEvent, eventPayload, state)
                } else {
                    SessionEventExecutor(eventKey, sessionEvent, state, instant)
                }
            }
            is StartFlow -> StartFlowExecutor(eventKey, FLOW_EVENT_TOPIC, sessionEvent, state)
            is ExecuteCleanup -> ExecuteCleanupEventExecutor(eventKey)
            is ScheduleCleanup -> ScheduleCleanupEventExecutor(eventKey, sessionEvent, state)

            else -> throw NotImplementedError(
                "The event type '${sessionEvent.javaClass.name}' is not supported.")
        }
    }
}
