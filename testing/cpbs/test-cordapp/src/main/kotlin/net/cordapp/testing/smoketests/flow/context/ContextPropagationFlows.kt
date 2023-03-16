package net.cordapp.testing.smoketests.flow.context

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable

@CordaSerializable
data class FlowOutput(
    val platform: String,
    val user1: String,
    val user2: String,
    val user3: String
)

@CordaSerializable
data class MainSubFlowOutput(
    val thisFlow: FlowOutput,
    val initiatedFlow: InitiatedFlowOutput
)

@CordaSerializable
data class InitiatedFlowOutput(
    val thisFlow: FlowOutput,
    val initiatedSubFlow: FlowOutput,
)

@CordaSerializable
data class ContextPropagationOutput(
    val rpcFlow: FlowOutput,
    val rpcSubFlow: FlowOutput,
    val initiatedFlow: FlowOutput,
    val initiatedSubFlow: FlowOutput,
    val rpcFlowAtComplete: FlowOutput,
)

private const val CORDA_ACCOUNT = "corda.account"
private const val ERROR_VALUE = "error"
private const val NULL_VALUE = "null"

/**
 * Launches multiple flows and extracts and collates configuration from each:
 *
 * main test flow (function launch point)
 *  -> ContextPropagationMainSubFlow sub flow
 *    -> ContextPropagationInitiatedFlow initiated flow
 *      -> ContextPropagationInitiatedSubFlow sub flow
 *
 * Because this test is not able to control the protocol for the main test flow, it cannot launch an initiated flow
 * itself, instead the protocols are local to the first sub flow launched.
 */
@Suspendable
fun launchContextPropagationFlows(
    flowEngine: FlowEngine,
    jsonMarshallingService: JsonMarshallingService
): String {
    val account = flowEngine.flowContextProperties.get(CORDA_ACCOUNT) ?: ERROR_VALUE

    flowEngine.flowContextProperties.put("user1", "user1-set")
    val user1 = flowEngine.flowContextProperties.get("user1") ?: ERROR_VALUE
    val user2 = flowEngine.flowContextProperties.get("user2") ?: NULL_VALUE
    val user3 = flowEngine.flowContextProperties.get("user3") ?: NULL_VALUE

    // Sub flow will send its context back
    val mainSubFlowOutput = flowEngine.subFlow(ContextPropagationMainSubFlow())

    val rpcFlowOutput = FlowOutput(
        platform = account,
        user1 = user1,
        user2 = user2,
        user3 = user3
    )

    // Refetch the original properties again to ensure nothing in the Flow execution path has corrupted them
    val rpcFlowOutputReFetchAtComplete = FlowOutput(
        platform = flowEngine.flowContextProperties.get(CORDA_ACCOUNT) ?: ERROR_VALUE,
        user1 = flowEngine.flowContextProperties.get("user1") ?: ERROR_VALUE,
        user2 = flowEngine.flowContextProperties.get("user2") ?: NULL_VALUE,
        user3 = flowEngine.flowContextProperties.get("user3") ?: NULL_VALUE
    )

    /* This is the expected output
       Values that are missing incorrectly will be marked as 'error'
    {
      "rpcFlow": {
        "platform": "account-zero",
        "user1": "user1-set",
        "user2": "null",
        "user3": "null"
      },
      "rpcSubFlow": {
        "platform": "account-zero",
        "user1": "user1-set",
        "user2": "user2-set",
        "user3": "null"
      },
      "initiatedFlow": {
        "platform": "account-zero",
        "user1": "user1-set",
        "user2": "user2-set",
        "user3": "user3-set"
      },
      "initiatedSubFlow": {
        "platform": "account-zero",
        "user1": "user1-set",
        "user2": "user2-set-ContextPropagationInitiatedFlow",
        "user3": "user3-set"
      },
      "rpcFlowAtComplete": {
        "platform": "account-zero",
        "user1": "user1-set",
        "user2": "null",
        "user3": "null"
      }
    }
    */
    return jsonMarshallingService.format(
        ContextPropagationOutput(
            rpcFlow = rpcFlowOutput,
            rpcSubFlow = mainSubFlowOutput.thisFlow,
            initiatedFlow = mainSubFlowOutput.initiatedFlow.thisFlow,
            initiatedSubFlow = mainSubFlowOutput.initiatedFlow.initiatedSubFlow,
            rpcFlowAtComplete = rpcFlowOutputReFetchAtComplete
        )
    )
}

@InitiatingFlow(protocol = "contextPropagationProtocol")
class ContextPropagationMainSubFlow : SubFlow<MainSubFlowOutput> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): MainSubFlowOutput {
        val account = flowEngine.flowContextProperties.get(CORDA_ACCOUNT) ?: ERROR_VALUE

        // Set context here so we can check this never makes it back to the parent flow, but does make it to the
        // initiated flow
        flowEngine.flowContextProperties.put("user2", "user2-set")

        val user1 = flowEngine.flowContextProperties.get("user1") ?: ERROR_VALUE

        val session = flowMessaging.initiateFlow(flowEngine.virtualNodeName) { flowContextProperties ->
            // user session specific context property
            flowContextProperties.put("user3", "user3-set")
        }

        // Initiated flow will send its context back via a message
        val initiatedFlowOutput = session.receive(InitiatedFlowOutput::class.java)

        // Check user 2 is preserved here, even though it is overwritten in the initiated sub flow
        val user2 = flowEngine.flowContextProperties.get("user2") ?: ERROR_VALUE
        // This should never make it out of this flow
        flowEngine.flowContextProperties.put("user2", "user2-set-ContextPropagationMainSubFlow")

        // user3 is session specific
        val user3 = flowEngine.flowContextProperties.get("user3") ?: NULL_VALUE

        return MainSubFlowOutput(
            thisFlow = FlowOutput(
                platform = account,
                user1 = user1,
                user2 = user2,
                user3 = user3
            ),
            initiatedFlow = initiatedFlowOutput
        )
    }
}

@InitiatedBy(protocol = "contextPropagationProtocol")
class ContextPropagationInitiatedFlow : ResponderFlow {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        val account = flowEngine.flowContextProperties.get(CORDA_ACCOUNT) ?: ERROR_VALUE
        val user1 = flowEngine.flowContextProperties.get("user1") ?: ERROR_VALUE
        // Get user2 and user3 on flow entry
        val user2 = flowEngine.flowContextProperties.get("user2") ?: NULL_VALUE
        val user3 = flowEngine.flowContextProperties.get("user3") ?: NULL_VALUE

        // This should never make it out of this flow, but should make it into the sub flow
        flowEngine.flowContextProperties.put("user2", "user2-set-ContextPropagationInitiatedFlow")

        val subFlowOutput = flowEngine.subFlow(ContextPropagationInitiatedSubFlow())

        session.send(
            InitiatedFlowOutput(
                thisFlow = FlowOutput(
                    platform = account,
                    user1 = user1,
                    user2 = user2,
                    user3 = user3
                ),
                initiatedSubFlow = subFlowOutput
            )
        )
    }
}

class ContextPropagationInitiatedSubFlow : SubFlow<FlowOutput> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): FlowOutput {
        val account = flowEngine.flowContextProperties.get(CORDA_ACCOUNT) ?: ERROR_VALUE

        val user1 = flowEngine.flowContextProperties.get("user1") ?: ERROR_VALUE
        // Get user2 and user3 on flow entry
        val user2 = flowEngine.flowContextProperties.get("user2") ?: NULL_VALUE
        val user3 = flowEngine.flowContextProperties.get("user3") ?: NULL_VALUE

        // These should never make it out of this flow
        flowEngine.flowContextProperties.put("user2", "user2-set-ContextPropagationInitiatedSubFlow")
        flowEngine.flowContextProperties.put("user3", "user3-set-ContextPropagationInitiatedSubFlow")

        return FlowOutput(
            platform = account,
            user1 = user1,
            user2 = user2,
            user3 = user3
        )
    }
}
