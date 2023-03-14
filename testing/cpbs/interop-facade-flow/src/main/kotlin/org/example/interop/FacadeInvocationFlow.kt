package org.example.interop

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

@InitiatingFlow("facade-invocation-flow")
class FacadeInvocationFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val alterEgoX500Name = MemberX500Name(
        commonName = "Alice",
        organization = "Other Alice Corp",
        locality = "LDN",
        country = "GB"
    )

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("FacadeInfocationFlow.call() starting...")

        val message = requestBody.getRequestBody()

        log.info("Calling facade with payload '$message'...")

        val response = flowMessaging.callFacade(
            memberName = alterEgoX500Name,
            facadeName = "None",
            methodName = "None",
            payload = message
        )

        log.info("Facade responded with '$response'")
        log.info("FacadeInvocationFlow.call() ending...")

        return response
    }
}
