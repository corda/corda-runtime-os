package net.corda.flow.mapper.impl

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionErrorExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.SessionInitExecutor
import net.corda.flow.mapper.impl.executor.StartFlowExecutor
import net.corda.flow.mapper.impl.executor.generateAppMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowMapperEventExecutorFactory::class])
class FlowMapperEventExecutorFactoryImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : FlowMapperEventExecutorFactory {

    private val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }

    override fun create(
        eventKey: String,
        flowMapperEvent: FlowMapperEvent,
        state: FlowMapperState?,
        flowConfig: SmartConfig,
        instant: Instant
    ): FlowMapperEventExecutor {
        return when (val flowMapperEventPayload = flowMapperEvent.payload) {
            is SessionEvent -> {
                when (val sessionPayload = flowMapperEventPayload.payload) {
                    is SessionInit -> {
                        SessionInitExecutor(
                            eventKey,
                            flowMapperEventPayload,
                            sessionPayload,
                            state,
                            sessionEventSerializer,
                            flowConfig
                        )
                    }
                    is SessionError -> {
                        SessionErrorExecutor(
                            eventKey,
                            flowMapperEventPayload,
                            state,
                            instant,
                            sessionEventSerializer,
                            ::generateAppMessage,
                            flowConfig
                        )
                    }
                    else -> {
                        SessionEventExecutor(
                            eventKey,
                            flowMapperEventPayload,
                            state,
                            instant,
                            sessionEventSerializer,
                            ::generateAppMessage,
                            flowConfig
                        )
                    }
                }
            }
            is StartFlow -> StartFlowExecutor(eventKey, FLOW_EVENT_TOPIC, flowMapperEventPayload, state)
            is ExecuteCleanup -> ExecuteCleanupEventExecutor(eventKey)
            is ScheduleCleanup -> ScheduleCleanupEventExecutor(eventKey, flowMapperEventPayload, state)

            else -> throw NotImplementedError(
                "The event type '${flowMapperEventPayload.javaClass.name}' is not supported."
            )
        }
    }
}
