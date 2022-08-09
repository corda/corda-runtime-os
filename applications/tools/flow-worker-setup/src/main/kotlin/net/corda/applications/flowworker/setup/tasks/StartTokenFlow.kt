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
import java.util.*

class StartTokenFlows(private val context: TaskContext) : Task {

    override fun execute() {

        val flowTargetAmounts = context.startArgs.targetAmount.ifEmpty {
            listOf(10L)
        }

        val startFlowEventRecords = flowTargetAmounts.map { getStartTokenSelectionFlow(context, it) }

        context.publish(startFlowEventRecords)
    }

    fun getStartTokenSelectionFlow(context: TaskContext, targetAmount: Long): Record<*, *> {
        val json = """{
    "tokenType": "coin",
    "issuerHash": "${context.startArgs.shortHolderId}",
    "notaryHash": "n1",
    "symbol": "USD",
    "targetAmount": ${targetAmount},
    "tagRegex": null,
    "ownerHash": null
}"""
        return getStartRPCEventRecord(
            requestId = UUID.randomUUID().toString(),
            flowName = "net.cordapp.flowworker.development.flows.TokenSelectionFlow",
            x500Name = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
            groupId = "flow-worker-dev",
            jsonArgs = json
        )
    }

    @Suppress("LongParameterList", "Unused")
    fun getStartRPCEventRecord(
        requestId: String,
        flowName: String,
        x500Name: String,
        groupId: String,
        jsonArgs: String
    ): Record<*, *> {
        val identity = HoldingIdentity(x500Name, groupId)

        val context = FlowStartContext(
            FlowKey(requestId, identity),
            FlowInitiatorType.RPC,
            requestId,
            identity,
            "flow-worker-dev",
            identity,
            flowName,
            jsonArgs,
            Instant.now()
        )

        val rpcStartFlow = StartFlow(context, jsonArgs)
        return Record(
            Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC,
            context.statusKey.toString(),
            FlowMapperEvent(rpcStartFlow)
        )
    }
}