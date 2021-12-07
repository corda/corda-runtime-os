package net.corda.flow.mapper.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.StartRPCFlowExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowMapperEventExecutorFactoryImplTest {

    val executorFactoryImpl = FlowMapperEventExecutorFactoryImpl()

    @Test
    fun testStartRPCFlowExecutor() {
        val meta = FlowMapperMetaData(FlowMapperEvent(), "", null, null, StartRPCFlow(), null, null, null)
        val executor = executorFactoryImpl.create(meta)
        assertThat(executor::class).isEqualTo(StartRPCFlowExecutor::class)
    }

    @Test
    fun testSessionEventExecutor() {
        val meta = FlowMapperMetaData(FlowMapperEvent(), "", null, null, SessionEvent(), null, null, null)
        val executor = executorFactoryImpl.create(meta)
        assertThat(executor::class).isEqualTo(SessionEventExecutor::class)
    }

    @Test
    fun testExecuteCleanupExecutor() {
        val meta = FlowMapperMetaData(FlowMapperEvent(), "", null, null, ExecuteCleanup(), null, null, null)
        val executor = executorFactoryImpl.create(meta)
        assertThat(executor::class).isEqualTo(ExecuteCleanupEventExecutor::class)
    }

    @Test
    fun testScheduleCleanupExecutor() {
        val meta = FlowMapperMetaData(FlowMapperEvent(), "", null, null, ScheduleCleanup(), null, null, null)
        val executor = executorFactoryImpl.create(meta)
        assertThat(executor::class).isEqualTo(ScheduleCleanupEventExecutor::class)
    }
}