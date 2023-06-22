package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.util.*

@InitiatingFlow(protocol = "swap-responder-sub-flow")
class SwapResponderSubFlow(private val session: FlowSession, private val alias: MemberX500Name, private val message: Payment):
    SubFlow<UUID> {

    @CordaInject
    lateinit var facadeService: FacadeService

    @Suspendable
    override fun call(): UUID {

        val facadeId = "org.corda.interop/platform/tokens/v1.0"
        log.info("Interop call: $facadeId, $alias, ${message.interopGroupId}")
        val tokens: TokensFacade =
            facadeService.getFacade(facadeId, TokensFacade::class.java, alias, message.interopGroupId)
        val responseObject = tokens.reserveTokensV1("USD", message.toReserve)
        log.info("Interop call finished}")
        val response = responseObject.result
        log.info("Interop call get $response}")
        session.send(response)

        return response
    }

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}
