package net.corda.flow.mapper.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.StartRPCFlowExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowMapperEventExecutorFactoryImplTest {

    private val executorFactoryImpl = FlowMapperEventExecutorFactoryImpl()

    @Test
    fun testStartRPCFlowExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(StartRPCFlow()), null)
        assertThat(executor::class).isEqualTo(StartRPCFlowExecutor::class)
    }

    @Test
    fun testSessionEventExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(SessionEvent()), null)
        assertThat(executor::class).isEqualTo(SessionEventExecutor::class)
    }

    @Test
    fun testExecuteCleanupExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(ExecuteCleanup()), null)
        assertThat(executor::class).isEqualTo(ExecuteCleanupEventExecutor::class)
    }

    @Test
    fun testScheduleCleanupExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(ScheduleCleanup()), null)
        assertThat(executor::class).isEqualTo(ScheduleCleanupEventExecutor::class)
    }
}