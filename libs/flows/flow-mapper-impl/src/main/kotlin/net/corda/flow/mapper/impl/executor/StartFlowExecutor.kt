package net.corda.flow.mapper.impl.executor

import net.corda.data.crypto.SecureHash
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import net.corda.v5.base.util.ByteArrays.toHexString
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*

class StartFlowExecutor(
    private val eventKey: String,
    private val outputTopic: String,
    private val startRPCFlow: StartFlow,
    private val flowMapperState: FlowMapperState?,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            CordaMetrics.Metric.FlowMapperCreationCount.builder()
                .withTag(CordaMetrics.Tag.FlowEvent, startRPCFlow::class.java.name)
                .build().increment()
            val clientIDSecureHash =
                SecureHash("SHA256", ByteBuffer.wrap(startRPCFlow.startContext.requestId.encodeToByteArray()))
            val flowId = toHexString(clientIDSecureHash.bytes.array()).substring(0, 8) + UUID.randomUUID().toString()
                .substring(8, 36)
            val newState = FlowMapperState(flowId, null, FlowMapperStateType.OPEN)
            val flowEvent = FlowEvent(flowId, startRPCFlow)
            FlowMapperResult(
                newState,
                mutableListOf(Record(outputTopic, flowId, flowEvent))
            )
        } else {
            CordaMetrics.Metric.FlowMapperDeduplicationCount.builder()
                .withTag(CordaMetrics.Tag.FlowEvent, startRPCFlow::class.java.name)
                .build().increment()
            log.debug { "Duplicate StartRPCFlow event received. Key: $eventKey, Event: $startRPCFlow " }
            FlowMapperResult(flowMapperState, mutableListOf())
        }
    }
}
