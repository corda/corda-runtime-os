package net.corda.flow.manager.impl.acceptance

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.virtualnode.VirtualNodeInfo
import java.time.Instant

fun getBasicFlowStartContext():FlowStartContext{
    val holdingIdentity = HoldingIdentity("x500 name","group id")
    val cpi = CPIIdentifier("cpi id","1.0",null)
    val virtualNode = VirtualNodeInfo(holdingIdentity,cpi)
    return  FlowStartContext.newBuilder()
        .setStatusKey(FlowStatusKey("request id",holdingIdentity))
        .setInitiatorType(FlowInitiatorType.RPC)
        .setClientRequestId("request id")
        .setVirtualNode(virtualNode)
        .setFlowClassName("flow class name")
        .setCreatedTimestamp(Instant.MIN)
        .build()
}