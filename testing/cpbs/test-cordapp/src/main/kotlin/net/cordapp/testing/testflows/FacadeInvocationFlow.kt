package net.cordapp.testing.testflows

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.corda.weft.dispatch.buildDispatcher
import org.corda.weft.facade.FacadeId
import org.corda.weft.facade.FacadeReaders
import org.corda.weft.facade.FacadeRequest
import org.corda.weft.facade.FacadeResponse
import org.corda.weft.parameters.ParameterType
import org.corda.weft.parameters.TypedParameter
import org.corda.weft.parameters.TypedParameterValue
import org.corda.weft.proxies.getClientProxy
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "invoke_facade_method")
class FacadeInvocationFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun getArgument(args: Map<String, String>, key: String): String {
            return checkNotNull(args[key]) { "Missing argument '$key'" }
        }
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("FacadeInvocationFlow.call() starting")

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val facadeName = getArgument(args, "facadeName")
        val methodName = getArgument(args, "methodName")
        val alias = MemberX500Name.parse(getArgument(args, "alias"))
        val payload = getArgument(args, "payload")

        log.info("Calling facade method '$methodName@$facadeName' with payload '$payload' to $alias")
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
        val client = facade.getClientProxy<SampleTokensFacade>(MessagingDispatcher(flowMessaging, alias, ""))
        val responseObject = client.getHello("Hi there!")
        //val response = flowMessaging.callFacade(alias, facadeName, methodName, payload)
        val response = responseObject.result
        log.info("Facade responded with '$response'")
        log.info("FacadeInvocationFlow.call() ending")

        return response
    }
}


class MessagingDispatcher(private var flowMessaging: FlowMessaging,private val alias: MemberX500Name, val aliasGroupId: String)
    : (FacadeRequest) -> FacadeResponse {
    override fun invoke(p1: FacadeRequest): FacadeResponse {
        val facade = p1.facadeId
        val method = p1.methodName
        val payload = p1.inParameters.toString()
        val response = flowMessaging.callFacade(alias, facade.toString(), method, payload)
        return FacadeResponse(p1.facadeId, method,
            listOf(TypedParameterValue(TypedParameter("greeting", ParameterType.StringType), response)))

    }

}