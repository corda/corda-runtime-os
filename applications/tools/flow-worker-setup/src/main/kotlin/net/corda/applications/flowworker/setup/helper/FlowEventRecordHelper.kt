package net.corda.applications.flowworker.setup.helper

import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import java.time.Instant


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
    return Record(FLOW_MAPPER_EVENT_TOPIC, flowKey, FlowMapperEvent(rpcStartFlow))
}

fun getHelloWorldScheduleCleanupEvent(): Record<*, *> {
    return getScheduleCleanupEvent("test123", "x500name", "123")
}

fun getScheduleCleanupEvent(clientId: String, x500Name: String, groupId: String): Record<*, *> {
    return Record(
        FLOW_MAPPER_EVENT_TOPIC, "$clientId.$x500Name.$groupId", FlowMapperEvent(
            ScheduleCleanup(
                System.currentTimeMillis() +
                        5000L
            )
        )
    )
}
