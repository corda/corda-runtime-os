package net.cordapp.flowworker.development.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.flows.set
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@CordaSerializable
data class FlowOutput(
    val platform: String,
    val user1: String,
    val user2: String
)

@CordaSerializable
data class InitiatedFlowOutput(
    val initiatedFlow: FlowOutput,
    val initiatedSubFlow: FlowOutput,
)

data class ContextPropagationOutput(
    val rpcFlow: FlowOutput,
    val rpcSubFlow: FlowOutput,
    val initiatedFlow: FlowOutput,
    val initiatedSubFlow: FlowOutput,
    val rpcFlowAtComplete: FlowOutput,
)

@InitiatingFlow(protocol = "contextPropagationProtocol")
class ContextPropagationFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val account = flowEngine.flowContextProperties.get("account") ?: "error"
        log.info("@@@ rpc flow: account from context:${account}")

        flowEngine.flowContextProperties.set("user1", "user1-set")
        val user1 = flowEngine.flowContextProperties.get("user1") ?: "error"
        log.info("@@@ rpc flow: user from context:${user1}")

        log.info("@@@ initiating session")
        val session = flowMessaging.initiateFlow(flowEngine.virtualNodeName)
        log.info("@@@ initiated")
        val initiatedFlowOutput = session.receive<InitiatedFlowOutput>().unwrap { it }
        log.info("@@@ received message")

        val subFlowOutput = flowEngine.subFlow(ChatSubFlow("1"))

        val user2 = flowEngine.flowContextProperties.get("user2") ?: "null"
        log.info("@@@ rpc flow: user2 from context:${user2}")

        val rpcFlowOutput = FlowOutput(
            platform = account,
            user1 = user1,
            user2 = user2
        )

        // Refetch the original properties again to ensure nothing in the Flow execution path has corrupted them
        val rpcFlowOutputReFetchAtComplete = FlowOutput(
            platform = flowEngine.flowContextProperties.get("account") ?: "error",
            user1 = flowEngine.flowContextProperties.get("user1") ?: "error",
            user2 = flowEngine.flowContextProperties.get("user2") ?: "null"
        )

        // All values should be set except user2 in non-sub flows which should be 'null'
        return jsonMarshallingService.format(
            ContextPropagationOutput(
                rpcFlow = rpcFlowOutput,
                rpcSubFlow = subFlowOutput,
                initiatedFlow = initiatedFlowOutput.initiatedFlow,
                initiatedSubFlow = initiatedFlowOutput.initiatedSubFlow,
                rpcFlowAtComplete = rpcFlowOutputReFetchAtComplete
            )
        )
    }
}

class ChatSubFlow(
    val tag: String
) : SubFlow<FlowOutput> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): FlowOutput {
        val account = flowEngine.flowContextProperties.get("account") ?: "error"
        log.info("@@@ sub flow {$tag}: account from context:${account}")

        flowEngine.flowContextProperties.set("user2", "user2-set")

        val user1 = flowEngine.flowContextProperties.get("user1") ?: "error"
        log.info("@@@ sub flow {$tag}: user from context:${user1}")
        val user2 = flowEngine.flowContextProperties.get("user2") ?: "error"
        log.info("@@@ sub flow {$tag}: user2 from context:${user2}")

        return FlowOutput(
            platform = account,
            user1 = user1,
            user2 = user2
        )
    }
}

@InitiatedBy(protocol = "contextPropagationProtocol")
class ChatIncomingFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var persistenceService: PersistenceService

    @Suspendable
    override fun call(session: FlowSession) {
        val account = flowEngine.flowContextProperties.get("account") ?: "error"
        log.info("@@@ initiated flow: account from context:${account}")

        val user1 = flowEngine.flowContextProperties.get("user1") ?: "error"
        log.info("@@@ initiated flow: user from context:${user1}")

        val subFlowOutput = flowEngine.subFlow(ChatSubFlow("2"))

        val user2 = flowEngine.flowContextProperties.get("user2") ?: "null"
        log.info("@@@ initiated flow: user2 from context:${user2}")

        session.send(
            InitiatedFlowOutput(
                initiatedFlow = FlowOutput(
                    platform = account,
                    user1 = user1,
                    user2 = user2
                ),
                initiatedSubFlow = subFlowOutput
            )
        )
    }
}