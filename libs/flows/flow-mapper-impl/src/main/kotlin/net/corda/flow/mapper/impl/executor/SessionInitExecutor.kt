package net.corda.flow.mapper.impl.executor

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class SessionInitExecutor(
    private val eventKey: String,
    private val sessionEvent: SessionEvent,
    private val sessionInit: SessionInit,
    private val flowMapperState: FlowMapperState?,
    private val sessionEventSerializer: CordaAvroSerializer<SessionEvent>,
    private val flowConfig: SmartConfig,
    private val sessionInitHelper: SessionInitHelper,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val messageDirection = sessionEvent.messageDirection

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            CordaMetrics.Metric.FlowMapperCreationCount.builder()
                .withTag(CordaMetrics.Tag.FlowEvent, sessionInit::class.java.name)
                .build().increment()
            sessionInitHelper.processSessionInit(sessionEvent, sessionInit, flowConfig)
        } else {
            //duplicate
            log.debug { "Duplicate SessionInit event received. Key: $eventKey, Event: $sessionEvent" }
            if (messageDirection == MessageDirection.OUTBOUND) {
                sessionInit.flowId = null
                FlowMapperResult(
                    flowMapperState,
                    listOf(
                        Record(
                            getSessionEventOutputTopic(messageDirection),
                            eventKey,
                            generateAppMessage(sessionEvent, sessionEventSerializer, flowConfig)
                        )
                    )
                )
            } else {
                CordaMetrics.Metric.FlowMapperDeduplicationCount.builder()
                    .withTag(CordaMetrics.Tag.FlowEvent, sessionInit::class.java.name)
                    .build().increment()
                FlowMapperResult(flowMapperState, emptyList())
            }
        }
    }
}
