package net.cordapp.flowworker.development.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordapp.flowworker.development.messages.TestFlowInput
import net.cordapp.flowworker.development.messages.TestFlowOutput

/**
 * The Test Flow exercises various basic features of a flow, this flow
 * is used as a basic flow worker smoke test.
 */
@Suppress("unused")
class TestFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Starting Test Flow...")
        try {
            val inputs = requestBody.getRequestBodyAs<TestFlowInput>(jsonMarshallingService)
            if(inputs.throwException){
                throw IllegalStateException("Caller requested exception to be raised")
            }

            val foundMemberInfo = if(inputs.memberInfoLookup==null){
                "No member lookup requested."
            }else{
                val lookupResult = memberLookupService.lookup(MemberX500Name.parse(inputs.memberInfoLookup!!))
                lookupResult?.name?.toString() ?: "Failed to find MemberInfo for ${inputs.memberInfoLookup!!}"
            }

            /**
             * For now this is removed to allow others to test while the issue preventing this
             * from working is investigated
            val subFlow = TestGetNodeNameSubFlow()
            val myIdentity = flowEngine.subFlow(subFlow)
            */

            val response = TestFlowOutput(
                inputs.inputValue?:"No input value",
                "dummy",
                foundMemberInfo
            )

            return jsonMarshallingService.format(response)

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow",e )
            throw e
        }
    }
}

