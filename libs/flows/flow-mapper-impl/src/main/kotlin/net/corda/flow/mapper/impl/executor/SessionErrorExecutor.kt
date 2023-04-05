package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import org.slf4j.LoggerFactory

class SessionErrorExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val flowMapperState: FlowMapperState?
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val errorMsg = "Flow mapper received error event from counterparty for session which does not exist. " +
            "Session may have expired. This error event will be %. Key: $eventKey, Event: $sessionEvent"

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            log.warn(errorMsg.format("Ignored"))
            FlowMapperResult(null, listOf())
        } else {
            processSessionErrorEvents(flowMapperState)
        }
    }

    private fun processSessionErrorEvents(flowMapperState: FlowMapperState): FlowMapperResult {
        when (flowMapperState.status) {
            FlowMapperStateType.ERROR -> {
                log.warn(errorMsg.format("Ignored"))
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.OPEN -> {
                log.warn(errorMsg.format("Forwarded"))
                FlowMapperResult(null, listOf())
            }
            FlowMapperStateType.CLOSING -> {
                log.warn(errorMsg.format("Ignored"))
                FlowMapperResult(null, listOf())
            }
        }
        log.warn(errorMsg.format("Ignored"))
        return FlowMapperResult(null, listOf())
    }
}