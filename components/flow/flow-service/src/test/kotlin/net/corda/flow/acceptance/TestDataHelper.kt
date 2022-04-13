package net.corda.flow.acceptance

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.identity.HoldingIdentity
import java.time.Instant

fun getBasicFlowStartContext(): FlowStartContext {
    val holdingIdentity = HoldingIdentity("x500 name", "group id")
    return FlowStartContext.newBuilder()
        .setStatusKey(FlowKey("request id", holdingIdentity))
        .setInitiatorType(FlowInitiatorType.RPC)
        .setRequestId("request id")
        .setIdentity(holdingIdentity)
        .setCpiId("cpi id")
        .setInitiatedBy(holdingIdentity)
        .setFlowClassName("flow class name")
        .setCreatedTimestamp(Instant.MIN)
        .build()
}