package net.cordapp.testing.testflows

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.corda.weft.binding.api.InteropAction
import org.corda.weft.facade.FacadeId3
import org.corda.weft.facade.FacadeReaders
import org.corda.weft.facade.FacadeRequest
import org.corda.weft.parameters.TypedParameterValue
import org.slf4j.LoggerFactory
import org.corda.weft.dispatch.buildDispatcher
import org.corda.weft.facade.FacadeRequestDeserializer
import org.corda.weft.parameters.ParameterType
import org.corda.weft.parameters.TypedParameter

@InitiatedBy(protocol = "invoke_facade_method")
class FacadeInvocationResponderFlow : ResponderFlow , SampleTokensFacade {
    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @Suspendable
    override fun call(session: FlowSession) {
        log.info("FacadeInvocationResponderFlow with proxy and serilizer 3 .call() starting")
        val request = session.receive(String::class.java)
        log.info("FacadeInvocationResponderFlow with proxy and serilizer 3 .call() received : $request")
        //val om = ObjectMapper()
        //om.registerSubtypes(FacadeRequest::class.java)
        //val facadeRequest : FacadeRequest = om.readValue(request, FacadeRequest::class.java)

        val facadeRequest  = jsonMarshallingService.parse(request, FacadeRequest::class.java)
       //  com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of `org.corda.weft.parameters.ParameterType` (no Creators, like default constructor, exist): abstract types either need to be mapped to concrete types, have custom deserializer, or contain additional type information

        log.info("FacadeInvocationResponderFlow with proxy and serilizer 3 .call() parsed : $facadeRequest")
        //val response = "$request:Bye"
        //val facadeRequest = FacadeRequest(FacadeId3("", mutableListOf("com", "r3", "tokens", "sample").joinToString("/"), "v1.0"),
        //    "hello", listOf(TypedParameterValue(TypedParameter("greeting", ParameterType.StringType), request)))
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
                        },
                        "get-balance": {
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
        val dispatcher = buildDispatcher(facade, jsonMarshallingService)
        val facadeResponse = dispatcher.invoke(facadeRequest)
        val response = facadeResponse.outParameters.first().value
        log.info("FacadeInvocationResponderFlow with proxy 1.call(): received=$request, response=$response")
        session.send(response)
    }

    override fun getHello(greeting: String): InteropAction<String> {
        return InteropAction.ServerResponse("$greeting -> Bye from proxy1")
    }

    override fun getBalance(greeting: String): InteropAction<String> {
        return InteropAction.ServerResponse("100")
    }
}