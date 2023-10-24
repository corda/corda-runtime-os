package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import net.corda.v5.application.interop.facade.FacadeId

@Suppress("LongParameterList")
class ReserveTokensSubFlowV2(private val alias: MemberX500Name, private val interopGroupId: String,
                             private val facadeId: FacadeId, private val denomination: String,
                             private val amount: BigDecimal, private val timeToLiveMs: Long):
    SubFlow<TokenReservation> {

    @CordaInject
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var interopIdentityLookUp: InteropIdentityLookup

    @Suspendable
    override fun call(): TokenReservation {
        log.info("${this::class.java.simpleName}.call() starting")
        log.info("Calling facade method '$facadeId' to $alias")

        val identityInfo = interopIdentityLookUp.lookup(alias.organization)

        val client: TokensFacade =
            facadeService.getProxy(facadeId, TokensFacade::class.java, identityInfo)

        val response: TokenReservation = client.reserveTokensV2(denomination, amount, timeToLiveMs)

        log.info("Facade responded with '$response'")
        log.info("${this::class.java.simpleName}.call() ending")

        return response
    }

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}
