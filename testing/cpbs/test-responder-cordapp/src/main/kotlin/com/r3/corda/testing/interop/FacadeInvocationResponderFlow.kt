package com.r3.corda.testing.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.membership.MemberLookup
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

//Following protocol name is deliberately used to
// prove that interop is not using protocol string to start the responder flow
@InitiatedBy(protocol = "dummy_protocol")
class FacadeInvocationResponderFlow : FacadeDispatcherFlow(), SampleTokensFacade {

    @CordaInject
    lateinit var memberLookup: MemberLookup

    override fun processHello(greeting: String): String {
        val name = memberLookup.myInfo().name
        return "$greeting -> Hello, my real name is $name"
    }

    override fun reserveTokensV2(denomination: String,
                                 amount: BigDecimal,
                                 timeToLiveMs: Long): TokenReservation {
        return TokenReservation(UUID.randomUUID(), ZonedDateTime.now())
    }
}
