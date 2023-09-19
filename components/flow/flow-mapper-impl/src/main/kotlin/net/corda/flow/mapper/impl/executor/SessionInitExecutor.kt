package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList")
class SessionInitExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val sessionInit: SessionInit,
    private val flowMapperState: FlowMapperState?,
    private val flowConfig: SmartConfig,
    private val recordFactory: RecordFactory,
    private val instant: Instant,
    private val sessionInitProcessor: SessionInitProcessor,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val messageDirection = sessionEvent.messageDirection

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            sessionInitProcessor.processSessionInit(sessionEvent, sessionInit, flowConfig, instant)
        } else {
            //duplicate
            log.debug { "Duplicate SessionInit event received. Key: $eventKey, Event: $sessionEvent" }
            if (messageDirection == MessageDirection.OUTBOUND) {
                val tmpFlowId = sessionInit.flowId
                sessionInit.flowId = null
                val outputRecord = recordFactory.forwardEvent(sessionEvent, instant, flowConfig, tmpFlowId)
                FlowMapperResult(flowMapperState, listOf(outputRecord))
            } else {
                CordaMetrics.Metric.FlowMapperDeduplicationCount.builder()
                    .withTag(CordaMetrics.Tag.FlowEvent, sessionInit::class.java.name)
                    .build().increment()
                FlowMapperResult(flowMapperState, emptyList())
            }
        }
    }
}
