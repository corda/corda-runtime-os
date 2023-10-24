package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InterOpIdentityInfo
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.util.UUID
import net.corda.v5.application.interop.facade.FacadeId

@InitiatingFlow(protocol = "swap-responder-sub-flow")
class SwapResponderSubFlow(private val message: Payment):
    SubFlow<UUID> {

    @CordaInject
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var interopIdentityLookUp : InteropIdentityLookup

    @Suspendable
    override fun call(): UUID {

        val applicationName = message.applicationName
        val myInteropInfo : InterOpIdentityInfo? = interopIdentityLookUp.lookup(applicationName)
        require(myInteropInfo != null) { "Can't get InteropIdentityInfo for ${applicationName}." }
        val facadeId = FacadeId("org.corda.interop", listOf("platform", "tokens"), "v1.0")
        log.info("Interop call: facadeId=$facadeId, interopIdentity=${myInteropInfo.applicationName}," +
                " interopGroupId=${myInteropInfo.groupId}")
        val tokens: TokensFacade =
            facadeService.getProxy(facadeId, TokensFacade::class.java, myInteropInfo)
        val response = tokens.reserveTokensV1("USD", message.toReserve)
        log.info("Interop call returned: $response")

        return response
    }

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}
