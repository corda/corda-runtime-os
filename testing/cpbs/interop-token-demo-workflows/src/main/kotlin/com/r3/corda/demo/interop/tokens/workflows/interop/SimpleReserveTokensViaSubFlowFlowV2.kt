package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import net.corda.v5.application.interop.facade.FacadeId

@InitiatingFlow(protocol = "SimpleReserveTokensViaSubFlow2-protocol")
class SimpleReserveTokensViaSubFlowFlowV2 : ClientStartableFlow {
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
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("${this::class.java.simpleName}.call() starting")

        val args = requestBody.getRequestBodyAsMap(jsonMarshallingService, String::class.java, String::class.java)

        val interopGroupId = getArgument(args, "interopGroupId")
        val facadeId = FacadeId.of(getArgument(args, "facadeId"))
        val alias = MemberX500Name.parse(getArgument(args, "alias"))
        val uuid = getArgument(args, "payload")

        log.info("Calling facade method '$facadeId' with payload '$uuid' to $alias")

        val response: TokenReservation = flowEngine.subFlow(ReserveTokensSubFlowV2(alias, interopGroupId,
            facadeId, "GBP",BigDecimal(200), 100))

        val text = response.toString()
        log.info("Facade responded with '$text'")
        log.info("${this::class.java.simpleName}.call() ending")

        return text
    }
}
