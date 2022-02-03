package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import java.time.Instant
import java.util.*

class StartFlow(private val context: TaskContext) : Task {

    override fun execute() {
        context.publish(
            getStartRPCEventRecord(
                clientId = UUID.randomUUID().toString(),
                cpiId = "corda-helloworld-cpb",
                flowName = "net.corda.linearstatesample.flows.HelloWorldFlowInitiator",
                x500Name = context.startArgs.x500NName ,
                groupId = "1",
                jsonArgs = "{ \"who\":\"${context.startArgs.x500NName}\"}"
            )
        )
    }

    @Suppress("LongParameterList")
    fun getStartRPCEventRecord(
        clientId: String,
        cpiId: String,
        flowName: String,
        x500Name: String,
        groupId: String,
        jsonArgs: String
    ): Record<*, *> {
        val identity = HoldingIdentity(x500Name, groupId)
        val flowKey = "$clientId.$x500Name.$groupId"
        val rpcStartFlow = StartRPCFlow(clientId, cpiId, flowName, identity, Instant.now(), jsonArgs)
        return Record(
            Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC,
            flowKey,
            FlowMapperEvent(rpcStartFlow)
        )
    }
}