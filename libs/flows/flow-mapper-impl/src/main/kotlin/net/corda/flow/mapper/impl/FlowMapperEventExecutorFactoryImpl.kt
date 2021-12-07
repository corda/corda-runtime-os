package net.corda.flow.mapper.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.StartRPCFlowExecutor
import org.osgi.service.component.annotations.Component

@Component(service = [FlowMapperEventExecutorFactory::class])
class FlowMapperEventExecutorFactoryImpl : FlowMapperEventExecutorFactory{

    override fun create(flowMetaData: FlowMapperMetaData): FlowMapperEventExecutor {
        return when (flowMetaData.payload) {
            is SessionEvent -> SessionEventExecutor(flowMetaData)
            is StartRPCFlow -> StartRPCFlowExecutor(flowMetaData)
            is ExecuteCleanup -> ExecuteCleanupEventExecutor()
            is ScheduleCleanup -> ScheduleCleanupEventExecutor(flowMetaData)

            else -> throw NotImplementedError(
                "The event type '${flowMetaData.payload.javaClass.name}' is not supported.")
        }
    }
}
