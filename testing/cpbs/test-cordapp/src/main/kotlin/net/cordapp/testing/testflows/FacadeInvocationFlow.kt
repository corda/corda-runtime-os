package net.cordapp.testing.testflows

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "invoke_facade_method")
class FacadeInvocationFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun getArgument(args: Map<String, String>, key: String): String {
            return checkNotNull(args[key]) { "Missing argument '$key'" }
        }
    }

    private val alterEgoX500Name = MemberX500Name(
        commonName = "Alice",
        organization = "Other Alice Corp",
        locality = "LDN",
        country = "GB"
    )

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("FacadeInfocationFlow.call() starting")

        val args = requestBody.getRequestBodyAs<Map<String, String>>(jsonMarshallingService)

        val facadeName = getArgument(args, "facadeName")
        val methodName = getArgument(args, "methodName")
        val payload = getArgument(args, "payload")

        log.info("Calling facade method '$methodName@$facadeName' with payload '$payload'")

        val response = flowMessaging.callFacade(
            memberName = alterEgoX500Name,
            facadeName = facadeName,
            methodName = methodName,
            payload = payload
        )

        log.info("Facade responded with '$response'")
        log.info("FacadeInvocationFlow.call() ending")

        return response
    }
}
