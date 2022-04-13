package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import java.time.Instant
import java.util.UUID

class StartFlow(private val context: TaskContext) : Task {

    override fun execute() {
        context.publish(
            getStartRPCEventRecord(
                clientId = UUID.randomUUID().toString(),
                flowName = "net.corda.flowworker.development.flows.MessagingFlow",
                x500Name = context.startArgs.x500NName,
                groupId = "flow-worker-dev",
                jsonArgs = "{ \"who\":\"${context.startArgs.x500NName}\"}"
            )
        )
    }

    @Suppress("LongParameterList", "Unused")
    fun getStartRPCEventRecord(
        clientId: String,
        flowName: String,
        x500Name: String,
        groupId: String,
        jsonArgs: String
    ): Record<*, *> {
        val identity = HoldingIdentity(x500Name, groupId)

        val context = FlowStartContext(
            FlowKey(clientId, identity),
            FlowInitiatorType.RPC,
            clientId,
            identity,
            "flow-worker-dev",
            identity,
            flowName,
            Instant.now()
        )

        val rpcStartFlow = StartFlow(context, jsonArgs)
        return Record(
            Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC,
            context.statusKey,
            FlowMapperEvent(rpcStartFlow)
        )
    }
}