package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookUp
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.interop.InterOpIdentityInfo
import org.slf4j.LoggerFactory
import java.util.UUID

@InitiatingFlow(protocol = "swap-responder-sub-flow")
class SwapResponderSubFlow(private val message: Payment):
    SubFlow<UUID> {

    @CordaInject
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var interopIdentityLookUp : InteropIdentityLookUp

    @Suspendable
    override fun call(): UUID {

        val interopGroupId = message.interopGroupId
        val myInteropInfo : InterOpIdentityInfo? = interopIdentityLookUp.lookup(interopGroupId)
        require(myInteropInfo != null) { "Cant find InteropInfo for ${interopGroupId}." }
        val interopIdentity = MemberX500Name.parse(myInteropInfo.x500Name)
        val facadeId = "org.corda.interop/platform/tokens/v1.0"
        log.info("Interop call: facadeId=$facadeId, interopIdentity=$interopIdentity, interopGroupId=${interopGroupId}")
        val tokens: TokensFacade =
            facadeService.getProxy(facadeId, TokensFacade::class.java, interopIdentity, interopGroupId)
        val response = tokens.reserveTokensV1("USD", message.toReserve)
        log.info("Interop call returned: $response")

        return response
    }

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}
