package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InterOpIdentityInfo
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import net.corda.v5.application.interop.facade.FacadeId

@InitiatingFlow(protocol = "swap-responder-sub-flow")
class SwapResponderSubFlow(private val applicationName: String, private val otherLedgerRecipient: String,
                           private val otherLedgerAssetId: String,
                           private val notaryKey: ByteBuffer, private val draftHash: SecureHash):
    SubFlow<String> {

    @CordaInject
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var interopIdentityLookUp : InteropIdentityLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(): String {

        val myInteropInfo : InterOpIdentityInfo? = interopIdentityLookUp.lookup(applicationName)
        require(myInteropInfo != null) { "Can't get InteropIdentityInfo for ${applicationName}." }
        val facadeId = FacadeId("org.corda.interop", listOf("platform", "lock"), "v1.0")
        log.info("Interop call: facadeId=$facadeId, interopIdentity=${myInteropInfo.applicationName}," +
                " interopGroupId=${myInteropInfo.groupId}")

        val lockFacade: LockFacade =
            facadeService.getProxy(facadeId, LockFacade::class.java, myInteropInfo)

        val notaries = notaryLookup.notaryServices
        require(notaries.isNotEmpty()) { "No notaries are available." }
        require(notaries.size == 1) { "Too many notaries $notaries." }

        log.info("locking send to $myInteropInfo")
        val response = lockFacade.createLock(otherLedgerAssetId,
            otherLedgerRecipient, notaryKey, draftHash.toString())

        log.info("Interop call returned: $response")

        return response
    }

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}
