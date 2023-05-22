package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class ReserveTokensSubFlowV2(private val alias: MemberX500Name, private val interopGroupId: String,
                             private val facadeId: String, private val denomination: String,
                             private val amount: BigDecimal, private val timeToLiveMs : Long):
    SubFlow<TokenReservation> {

    @CordaInject
    lateinit var facadeService: FacadeService

    @Suspendable
    override fun call(): TokenReservation {
        log.info("ReserveTokensSubFlow.call() starting")

        log.info("Calling facade method '$facadeId' to $alias")

        val client: TokensFacade =
            facadeService.getFacade(facadeId, TokensFacade::class.java, alias, interopGroupId)

        val responseObject: InteropAction<TokenReservation> = client.reserveTokensV2(denomination, amount, timeToLiveMs)
        val response = responseObject.result

        log.info("Facade responded with '$response'")
        log.info("ReserveTokensSubFlow.call() ending")

        return response
    }

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}
