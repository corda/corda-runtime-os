package net.cordapp.testing

import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable

class SigningFlow : RPCStartableFlow {

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var signingService: SigningService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val myPublicKey = memberLookupService.myInfo().sessionInitiationKey
        val bytes = byteArrayOf(1, 2, 3)
        val signedData = signingService.sign(bytes, myPublicKey)
        println(signedData)
        return "{}"
    }
}