package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperMetaData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScheduleCleanupEventExecutorTest {

    @Test
    fun testScheduleCleanupEventExecutor() {
        val meta =
            FlowMapperMetaData(FlowMapperEvent(), "", null, null, ScheduleCleanup(Long.MAX_VALUE), FlowMapperState(), null, Long.MAX_VALUE)
        val result = ScheduleCleanupEventExecutor(meta).execute()
        assertThat(result.flowMapperState?.expiryTime).isEqualTo(Long.MAX_VALUE)
        assertThat(result.flowMapperState?.status).isEqualTo(FlowMapperStateType.CLOSING)
        assertThat(result.outputEvents).isEmpty()
    }

    @Test
    fun testScheduleCleanupEventExecutorNullState() {
        val meta = FlowMapperMetaData(FlowMapperEvent(), "", null, null, ScheduleCleanup(Long.MAX_VALUE), null, null, Long.MAX_VALUE)
        val result = ScheduleCleanupEventExecutor(meta).execute()
        assertThat(result.flowMapperState).isNull()
    }
}