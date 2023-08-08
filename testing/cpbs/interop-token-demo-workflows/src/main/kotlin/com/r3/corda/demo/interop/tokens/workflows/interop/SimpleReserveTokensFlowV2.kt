package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookUp
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.math.BigDecimal

@InitiatingFlow(protocol = "SimpleReserveTokensFlow2-protocol")
class SimpleReserveTokensFlowV2 : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @Suspendable
        private fun getArgument(args: Map<String, String>, key: String): String {
            return checkNotNull(args[key]) { "Missing argument '$key'" }
        }
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var interopIdentityLookUp: InteropIdentityLookUp

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("${this::class.java.simpleName}.call() starting")

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val facadeId = getArgument(args, "facadeId")
        val alias = MemberX500Name.parse(getArgument(args, "alias"))
        val uuid = getArgument(args, "payload")

        val interopIdentity = interopIdentityLookUp.lookup(alias.organization)

        log.info("Calling facade method '$facadeId' with payload '$uuid' to $alias")

        val tokens: TokensFacade =
            facadeService.getProxy(facadeId, TokensFacade::class.java, interopIdentity)

        val responseObject: TokenReservation = tokens.reserveTokensV2("USD", BigDecimal(100), 100)
        val response = responseObject.toString()

        log.info("Facade responded with '$response'")
        log.info("${this::class.java.simpleName}.call() ending")

        return response
    }
}
