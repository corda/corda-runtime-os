package net.cordapp.testing.testflows

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.corda.weft.binding.api.InteropAction
import org.corda.weft.facade.FacadeId
import org.corda.weft.facade.FacadeReaders
import org.corda.weft.facade.FacadeRequest
import org.corda.weft.parameters.TypedParameterValue
import org.slf4j.LoggerFactory
import org.corda.weft.dispatch.buildDispatcher
import org.corda.weft.parameters.ParameterType
import org.corda.weft.parameters.TypedParameter
import org.corda.weft.proxies.getClientProxy

@InitiatedBy(protocol = "invoke_facade_method")
class FacadeInvocationResponderFlow : ResponderFlow , SampleTokensFacade {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @Suspendable
    override fun call(session: FlowSession) {
        log.info("FacadeInvocationResponderFlow.call() starting")
        val request = session.receive(String::class.java)
        //val response = "$request:Bye"
        val facadeRequest = FacadeRequest(FacadeId("", listOf("com/r3/tokens/sample"), "v1.0"),
            "hello",  listOf(TypedParameterValue(TypedParameter("greeting", ParameterType.StringType), request)))
        val facade = FacadeReaders.JSON.read(
            """{ "id": "/com/r3/tokens/sample/v1.0",
                  "commands": { 
                    "hello": {
                        "in": {
                            "greeting": "string"
                            },
                        "out": {
                            "greeting": "string"
                            }
                        } 
                    }
                }""".trimIndent()
        )
        val dispatcher = buildDispatcher(facade)
        val facadeResponse = dispatcher.invoke(facadeRequest)
        val response = facadeResponse.outParameters.first().value
        log.info("FacadeInvocationResponderFlow.call(): received=$request, response=$response")
        session.send(response)
    }

    override fun getHello(greeting: String): InteropAction<String> {
        return InteropAction.ServerResponse("$greeting -> Bye")
    }
}