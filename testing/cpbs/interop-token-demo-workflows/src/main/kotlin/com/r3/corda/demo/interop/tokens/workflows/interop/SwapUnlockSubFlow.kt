package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InterOpIdentityInfo
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory
import java.util.UUID


@InitiatingFlow(protocol = "swap-unlock-sub-flow")
class SwapUnlockSubFlow(private val applicationName: String, private val proof: DigitalSignatureAndMetadata,
                                 private val lockedState: String):
    SubFlow<UUID> {

    @CordaInject
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var interopIdentityLookUp : InteropIdentityLookup

    @Suspendable
    override fun call(): UUID {

        val myInteropInfo : InterOpIdentityInfo? = interopIdentityLookUp.lookup(applicationName)
        require(myInteropInfo != null) { "Can't get InteropIdentityInfo for ${applicationName}." }
        val facadeId = "org.corda.interop/platform/lock/v1.0"
        log.info("Interop call: facadeId=$facadeId, interopIdentity=${myInteropInfo.applicationName}," +
                " interopGroupId=${myInteropInfo.groupId}")
        log.info("unlocking send to $myInteropInfo")
        val lockFacade: LockFacade =
            facadeService.getProxy(facadeId, LockFacade::class.java, myInteropInfo)

        val response = lockFacade.unlock(lockedState, proof)

        log.info("Interop call returned: $response")

        return UUID.randomUUID()
    }

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
}
