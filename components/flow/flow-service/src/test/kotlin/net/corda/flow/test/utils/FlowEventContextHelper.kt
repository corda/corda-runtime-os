package net.corda.flow.test.utils

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.FLOW_ID_1
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.FlowConfig
import org.mockito.kotlin.mock

@Suppress("LongParameterList")
fun <T> buildFlowEventContext(
    checkpoint: FlowCheckpoint,
    inputEventPayload: T,
    config: SmartConfig = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty()),
    outputRecords: List<Record<*, *>> = emptyList(),
    flowId: String = FLOW_ID_1,
    sendToDlq: Boolean = false,
    isRetryEvent: Boolean = false
): FlowEventContext<T> {


    val configWithRequired = config.withFallback(SmartConfigImpl.empty()
        .withValue(FlowConfig.SESSION_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(10000))
        .withValue(FlowConfig.PROCESSING_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(10000))
        .withValue(FlowConfig.EXTERNAL_EVENT_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(100))
        .withValue(FlowConfig.SESSION_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(5000))
    )

    return FlowEventContext(
        checkpoint,
        FlowEvent(flowId, inputEventPayload),
        inputEventPayload,
        mapOf(ConfigKeys.FLOW_CONFIG to configWithRequired),
        configWithRequired,
        isRetryEvent,
        outputRecords,
        sendToDlq,
        emptyMap(),
        mock(),
        mock(),
        null
    )
}

@Suppress("LongParameterList")
fun <T> buildFlowEventContext(
    inputEventPayload: T,
    config: SmartConfig = SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty()),
    outputRecords: List<Record<*, *>> = emptyList(),
    flowId: String = FLOW_ID_1,
    sendToDlq: Boolean = false,
    isRetryEvent: Boolean = false
): FlowEventContext<T> {
    return FlowEventContext(
        mock(),
        FlowEvent(flowId, inputEventPayload),
        inputEventPayload,
        emptyMap(),
        config,
        isRetryEvent,
        outputRecords,
        sendToDlq,
        emptyMap(),
        mock(),
        mock(),
        null
    )
}
