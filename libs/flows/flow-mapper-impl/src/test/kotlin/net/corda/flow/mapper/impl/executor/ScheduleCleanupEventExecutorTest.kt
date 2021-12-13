package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScheduleCleanupEventExecutorTest {

    private val eventKey = "key"

    @Test
    fun testScheduleCleanupEventExecutor() {
        val result = ScheduleCleanupEventExecutor(eventKey, ScheduleCleanup(Long.MAX_VALUE), FlowMapperState()).execute()
        assertThat(result.flowMapperState?.expiryTime).isEqualTo(Long.MAX_VALUE)
        assertThat(result.flowMapperState?.status).isEqualTo(FlowMapperStateType.CLOSING)
        assertThat(result.outputEvents).isEmpty()
    }

    @Test
    fun testScheduleCleanupEventExecutorNullState() {
        val result = ScheduleCleanupEventExecutor(eventKey, ScheduleCleanup(Long.MAX_VALUE), null).execute()
        assertThat(result.flowMapperState).isNull()
    }
}