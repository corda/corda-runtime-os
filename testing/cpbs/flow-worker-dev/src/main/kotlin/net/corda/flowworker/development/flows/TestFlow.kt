package net.corda.flowworker.development.flows

import net.corda.flowworker.development.messages.TestFlowInput
import net.corda.flowworker.development.messages.TestFlowOutput
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

/**
 * The Test Flow exercises various basic features of a flow, this flow
 * is used as a basic flow worker smoke test.
 */
@Suppress("unused")
@InitiatingFlow
@StartableByRPC
class TestFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(): String {
        log.info("Starting Test Flow...")
        try {
            val inputs = jsonMarshallingService.parseJson<TestFlowInput>(jsonArg)
            if(inputs.throwException){
                throw IllegalStateException("Caller requested exception to be raised")
            }

            val response = TestFlowOutput(
                inputs.inputValue?:"No input value",
                flowEngine.subFlow(TestGetNodeNameSubFlow()).toString()
            )

            return jsonMarshallingService.formatJson(response)

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow",e )
            throw e
        }
    }
}

