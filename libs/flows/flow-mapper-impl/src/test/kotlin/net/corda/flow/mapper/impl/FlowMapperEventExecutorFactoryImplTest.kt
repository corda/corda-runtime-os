package net.corda.flow.mapper.impl

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.mapper.impl.executor.ExecuteCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.ScheduleCleanupEventExecutor
import net.corda.flow.mapper.impl.executor.SessionEventExecutor
import net.corda.flow.mapper.impl.executor.StartFlowExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class FlowMapperEventExecutorFactoryImplTest {

    private val executorFactoryImpl = FlowMapperEventExecutorFactoryImpl(mock())

    @Test
    fun testStartRPCFlowExecutor() {
        val executor = executorFactoryImpl.create("", FlowMapperEvent(StartFlow()), null)
        assertThat(executor::class).isEqualTo(StartFlowExecutor::class)
    }

    @Test
    fun testSessionEventExecutor() {
        val executor = executorFactoryImpl.create("",
            FlowMapperEvent(SessionEvent(MessageDirection.INBOUND, Instant.now(), "", 1,
                HoldingIdentity(), HoldingIdentity(), 0, listOf(), null)),
            null)
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