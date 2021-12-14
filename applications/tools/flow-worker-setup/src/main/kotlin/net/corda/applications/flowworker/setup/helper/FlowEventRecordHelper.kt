package net.corda.applications.flowworker.setup.helper

import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import java.time.Instant

const val DEFAULT_FLOW_MAPPER_TOPIC_VALUE = "flow.mapper.event.topic"

fun getHelloWorldRPCEventRecord(): Record<*, *> {
    return getStartRPCEventRecord(
        clientId = "test123",
        cpiId = "corda-helloworld-cpb",
        flowName = "net.corda.linearstatesample.flows.HelloWorldFlowInitiator",
        x500Name = "x500name",
        groupId = "123"
    )
}

@Suppress("LongParameterList")
fun getStartRPCEventRecord(clientId: String, cpiId: String, flowName: String, x500Name: String, groupId: String):
        Record<*, *> {
    val identity = HoldingIdentity(x500Name, groupId)
    val flowKey = "$clientId.$x500Name.$groupId"
    val rpcStartFlow = StartRPCFlow(clientId, cpiId, flowName, identity, Instant.now(), "{ \"who\":\"world\"}")
    return Record(DEFAULT_FLOW_MAPPER_TOPIC_VALUE, flowKey, FlowMapperEvent(MessageDirection.INBOUND, rpcStartFlow))
}

fun getHelloWorldScheduleCleanupEvent(): Record<*, *> {
    return getScheduleCleanupEvent("test123", "x500name", "123")
}

fun getScheduleCleanupEvent(clientId: String, x500Name: String, groupId: String): Record<*, *> {
    return Record(
        DEFAULT_FLOW_MAPPER_TOPIC_VALUE, "$clientId.$x500Name.$groupId", FlowMapperEvent(
            MessageDirection.INBOUND,
            ScheduleCleanup(
                System.currentTimeMillis() +
                        5000L
            )
        )
    )
}
