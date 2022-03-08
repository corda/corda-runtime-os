package net.corda.flow.manager.impl.acceptance

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.identity.HoldingIdentity
import java.time.Instant

fun getBasicFlowStartContext(): FlowStartContext {
    val holdingIdentity = HoldingIdentity("x500 name", "group id")
    return FlowStartContext.newBuilder()
        .setStatusKey(FlowStatusKey("request id", holdingIdentity))
        .setInitiatorType(FlowInitiatorType.RPC)
        .setRequestId("request id")
        .setIdentity(holdingIdentity)
        .setCpiId("cpi id")
        .setInitiatedBy(holdingIdentity)
        .setFlowClassName("flow class name")
        .setCreatedTimestamp(Instant.MIN)
        .build()
}