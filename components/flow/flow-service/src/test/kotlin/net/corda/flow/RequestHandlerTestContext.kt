package net.corda.flow

import com.typesafe.config.ConfigValueFactory
import java.time.Instant
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.sessions.service.InitiateFlowRequestService
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowContext
import net.corda.flow.state.FlowStack
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.PROCESSING_FLOW_CLEANUP_TIME
import net.corda.schema.configuration.FlowConfig.SESSION_FLOW_CLEANUP_TIME
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toCorda
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RequestHandlerTestContext<PAYLOAD>(val payload: PAYLOAD) {
    val flowId = "flow id"
    val counterparty = MemberX500Name.parse("C=GB,L=London,O=Bob")
    val flowEvent = FlowEvent()
    val flowMessageFactory = mock<FlowMessageFactory>()
    val flowSessionManager = mock<FlowSessionManager>()
    val flowRecordFactory = mock<FlowRecordFactory>()
    val recordList = mutableListOf<Record<*, *>>()
    val flowStack = mock<FlowStack>()
    val holdingIdentity = BOB_X500_HOLDING_IDENTITY
    val flowStartContext = FlowStartContext()
    val flowCheckpoint = mock<FlowCheckpoint>()
    val flowContext = mock<FlowContext>()
    val flowConfig = SmartConfigImpl.empty()
        .withValue(SESSION_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(10000))
        .withValue(PROCESSING_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(10000))
    val flowSandboxService = mock<FlowSandboxService>()
    val initiateFlowReqService = mock<InitiateFlowRequestService>()

    init {
        flowStartContext.identity = holdingIdentity
        flowStartContext.createdTimestamp = Instant.now()
        flowStartContext.flowClassName = "net.corda.test.Flow"
        flowStartContext.statusKey = FlowKey("request id", holdingIdentity)

        whenever(flowCheckpoint.flowContext).thenReturn(flowContext)
        whenever(flowContext.flattenUserProperties()).thenReturn(emptyMap())
        whenever(flowContext.flattenPlatformProperties()).thenReturn(emptyMap())
        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.flowStack).thenReturn(flowStack)
        whenever(flowCheckpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(flowCheckpoint.holdingIdentity).thenReturn(holdingIdentity.toCorda())
    }

    val flowEventContext = FlowEventContext(flowCheckpoint, flowEvent, payload, flowConfig, recordList, mdcProperties = emptyMap())
}

