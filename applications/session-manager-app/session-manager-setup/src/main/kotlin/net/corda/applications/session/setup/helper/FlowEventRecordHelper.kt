package net.corda.applications.session.setup.helper

import net.corda.applications.common.ConfigHelper.Companion.DEFAULT_DEDUP_TOPIC_VALUE
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import java.time.Instant
import java.util.*

fun getHelloWorldRPCEventRecord() : Record<*, *>  {
    return getStartRPCEventRecord(
        clientId = "test123",
        cpiId = "1",
        flowId = "1",
        flowName = "net.corda.linearstatesample.flows.HelloWorldFlowInitiator",
        x500Name = "x500name",
        groupId = "123"
    )
}

@Suppress("LongParameterList")
fun getStartRPCEventRecord(clientId: String, cpiId: String, flowId: String, flowName: String, x500Name: String, groupId: String):
        Record<*, *> {
    val identity = HoldingIdentity(x500Name, groupId)
    val key = FlowKey(flowId, identity)
    val rpcStartFlow = StartRPCFlow(clientId, flowName, cpiId, identity, Instant.now(), Collections.emptyList())
    return Record(DEFAULT_DEDUP_TOPIC_VALUE, flowId, FlowEvent(key, rpcStartFlow))
}
