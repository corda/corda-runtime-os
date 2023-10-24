package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.*
import net.corda.v5.application.interop.facade.FacadeId

lateinit var interopIdentityLookUp: InteropIdentityLookup

@InitiatingFlow(protocol = "SimpleReserveTokensFlow-protocol")
class SimpleReserveTokensFlow : ClientStartableFlow {
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

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("${this::class.java.simpleName}.call() starting")

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val facadeId = FacadeId.of(getArgument(args, "facadeId"))
        val alias = MemberX500Name.parse(getArgument(args, "alias"))
        val uuid = getArgument(args, "payload")

        val identityInfo = interopIdentityLookUp.lookup(alias.organization)

        log.info("Calling facade method '$facadeId' with payload '$uuid' to $alias")

        val tokens: TokensFacade =
            facadeService.getProxy(facadeId, TokensFacade::class.java, identityInfo)

        val responseObject: UUID = tokens.reserveTokensV1("USD", BigDecimal(100))
        val response = responseObject.toString()

        log.info("Facade responded with '$response'")
        log.info("${this::class.java.simpleName}.call() ending")

        return response
    }
}
