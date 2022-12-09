package net.corda.flow.mapper.impl.executor

import java.util.Collections.emptyList
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class ScheduleCleanupEventExecutor(
    private val eventKey: String,
    private val scheduleCleanup: ScheduleCleanup,
    private val state: FlowMapperState?,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    override fun execute(): FlowMapperResult {
        return if (state == null) {
            log.debug { "Tried to cleanup mapper state which was already null on key $eventKey" }
            FlowMapperResult(state, emptyList())
        } else {
            state.status = FlowMapperStateType.CLOSING
            state.expiryTime = scheduleCleanup.expiryTime
            FlowMapperResult(state, emptyList())
        }
    }
}
