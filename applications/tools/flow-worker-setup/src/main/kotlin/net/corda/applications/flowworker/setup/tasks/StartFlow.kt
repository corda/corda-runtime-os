package net.corda.applications.flowworker.setup.tasks

import net.corda.applications.flowworker.setup.Task
import net.corda.applications.flowworker.setup.TaskContext
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import java.time.Instant
import java.util.UUID

class StartFlow(private val context: TaskContext) : Task {

    override fun execute() {

        val namedFlows = mapOf(
            "default" to getStartRPCEventRecord(
                clientId = UUID.randomUUID().toString(),
                flowName = "net.cordapp.flowworker.development.testflows.MessagingFlow",
                x500Name = context.startArgs.x500NName,
                groupId = "test-cordapp",
                jsonArgs = "{ \"who\":\"${context.startArgs.x500NName}\"}"
            ),

            "start_session_smoke_test" to getSmokeTestStartRecord(
                "{\"command\":\"start_sessions\",\"data\":{\"sessions\":\"CN=user1, O=user1 Corp, L=LDN, C=GB;CN=user2," +
                        " O=user2 Corp, L=LDN, C=GB\",\"messages\":\"m1;m2\"}}"
            ),

            "throw_platform_error_smoke_test" to getSmokeTestStartRecord(
                "{\"command\":\"throw_platform_error\",\"data\":{\"x500\":\"CN=user1, O=user1 Corp, L=LDN, C=GB\"}}"
            )
        )

        context.publish(
            checkNotNull(
                namedFlows[context.startArgs.flowName]
            ) { "Could not find named flow '${context.startArgs.flowName}'" }
        )
    }

    fun getSmokeTestStartRecord(args: String): Record<*, *> {
        return getStartRPCEventRecord(
            clientId = UUID.randomUUID().toString(),
            flowName = "net.cordapp.flowworker.development.flows.RpcSmokeTestFlow",
            x500Name = context.startArgs.x500NName,
            groupId = "test-cordapp",
            jsonArgs = args
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
            "test-cordapp",
            identity,
            flowName,
            jsonArgs,
            emptyKeyValuePairList(),
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