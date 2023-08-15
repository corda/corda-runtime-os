package net.corda.flow.mapper.impl.executor

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
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
    private val sessionEventSerializer: CordaAvroSerializer<SessionEvent>,
    private val flowConfig: SmartConfig,
    private val recordFactory: RecordFactory,
    private val instant: Instant
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val messageDirection = sessionEvent.messageDirection
    private val outputTopic = recordFactory.getSessionEventOutputTopic(sessionEvent, messageDirection)

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            CordaMetrics.Metric.FlowMapperCreationCount.builder()
                .withTag(CordaMetrics.Tag.FlowEvent, sessionInit::class.java.name)
                .build().increment()
            processSessionInit(sessionEvent, sessionInit)
        } else {
            //duplicate
            log.debug { "Duplicate SessionInit event received. Key: $eventKey, Event: $sessionEvent" }
            if (messageDirection == MessageDirection.OUTBOUND) {
                sessionInit.flowId = null
                val outputRecord = recordFactory.forwardEvent(sessionEvent, instant, flowConfig, messageDirection)
                FlowMapperResult(flowMapperState, listOf(outputRecord))
            } else {
                CordaMetrics.Metric.FlowMapperDeduplicationCount.builder()
                    .withTag(CordaMetrics.Tag.FlowEvent, sessionInit::class.java.name)
                    .build().increment()
                FlowMapperResult(flowMapperState, emptyList())
            }
        }
    }

    private fun processSessionInit(sessionEvent: SessionEvent, sessionInit: SessionInit): FlowMapperResult {
        val (flowKey, outputRecordKey, outputRecordValue) =
            getSessionInitOutputs(
                messageDirection,
                sessionEvent,
                sessionInit
            )

        return FlowMapperResult(
            FlowMapperState(flowKey, null, FlowMapperStateType.OPEN),
            listOf(Record(outputTopic, outputRecordKey, outputRecordValue))
        )
    }

    /**
     * Get a helper object to obtain:
     * - flow key
     * - output record key
     * - output record value
     */
    private fun getSessionInitOutputs(
        messageDirection: MessageDirection?,
        sessionEvent: SessionEvent,
        sessionInit: SessionInit,
    ): SessionInitOutputs {
        return if (messageDirection == MessageDirection.INBOUND) {
            val flowId = generateFlowId()
            sessionInit.flowId = flowId
            SessionInitOutputs(flowId, flowId, FlowEvent(flowId, sessionEvent))
        } else {
            //reusing SessionInit object for inbound and outbound traffic rather than creating a new object identical to SessionInit
            //with an extra field of flowKey. set flowkey to null to not expose it on outbound messages
            val tmpFLowEventKey = sessionInit.flowId
            sessionInit.flowId = null
            sessionEvent.payload = sessionInit

            SessionInitOutputs(
                tmpFLowEventKey,
                sessionEvent.sessionId,
                generateAppMessage(sessionEvent, sessionEventSerializer, flowConfig)
            )
        }
    }

    data class SessionInitOutputs(
        val flowId: String,
        val outputRecordKey: Any,
        val outputRecordValue: Any
    )
}
