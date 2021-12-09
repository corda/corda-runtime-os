package net.corda.applications.flowworker.setup.helper

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import java.time.Instant

const val DEFAULT_FLOW_EVENT_TOPIC_VALUE = "FlowEventTopic"

fun getHelloWorldRPCEventRecord() : Record<*, *>  {
    return getStartRPCEventRecord(
        clientId = "test123",
        cpiId = "corda-helloworld-cpb",
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
    val flowKey = FlowKey(flowId, identity)
    val rpcStartFlow = StartRPCFlow(clientId, cpiId, flowName, identity, Instant.now(), "{ \"who\":\"world\"}")
    return Record(DEFAULT_FLOW_EVENT_TOPIC_VALUE, flowKey, FlowEvent(flowKey, rpcStartFlow))
}
