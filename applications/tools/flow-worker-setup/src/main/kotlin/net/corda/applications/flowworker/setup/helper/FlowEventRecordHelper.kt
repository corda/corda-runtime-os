package net.corda.applications.flowworker.setup.helper

import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import java.time.Instant
import java.util.*


fun getHelloWorldRPCEventRecords(): List<Record<*, *>> {
    return listOf(
        getStartRPCEventRecord(
            clientId = UUID.randomUUID().toString(),
            cpiId = "corda-helloworld-cpb",
            flowName = "net.corda.linearstatesample.flows.HelloWorldFlowInitiator",
            x500Name = "Mars",
            groupId = "123",
            jsonArgs = "{ \"who\":\"Mars 1\"}"
        ),
        getStartRPCEventRecord(
            clientId = UUID.randomUUID().toString(),
            cpiId = "corda-helloworld-cpb",
            flowName = "net.corda.linearstatesample.flows.HelloWorldFlowInitiator",
            x500Name = "Mars",
            groupId = "123",
            jsonArgs = "{ \"who\":\"Mars 2\"}"
        ),
        getStartRPCEventRecord(
            clientId = UUID.randomUUID().toString(),
            cpiId = "corda-helloworld-cpb",
            flowName = "net.corda.linearstatesample.flows.HelloWorldFlowInitiator",
            x500Name = "Earth",
            groupId = "321",
            jsonArgs = "{ \"who\":\"Earth 1\"}"
        ),
        getStartRPCEventRecord(
            clientId = UUID.randomUUID().toString(),
            cpiId = "corda-helloworld-cpb",
            flowName = "net.corda.linearstatesample.flows.HelloWorldFlowInitiator",
            x500Name = "Earth",
            groupId = "321",
            jsonArgs = "{ \"who\":\"Earth 2\"}"
        ),
    )
}

@Suppress("LongParameterList")
fun getStartRPCEventRecord(clientId: String, cpiId: String, flowName: String, x500Name: String, groupId: String, jsonArgs: String):
        Record<*, *> {
    val identity = HoldingIdentity(x500Name, groupId)
    val flowKey = "$clientId.$x500Name.$groupId"
    val rpcStartFlow = StartRPCFlow(clientId, cpiId, flowName, identity, Instant.now(), jsonArgs)
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
