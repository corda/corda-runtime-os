package com.r3.corda.testing.interop

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.membership.MemberLookup


//Following protocol name is deliberately used to
// prove that interop is not using protocol string to start the responder flow
@InitiatedBy(protocol = "dummy_protocol")
class FacadeInvocationResponderFlow : FacadeDispatcherFlow() , SampleTokensFacade {

    @CordaInject
    lateinit var memberLookup: MemberLookup

    override fun processHello(greeting: String): InteropAction<String> {
        val name = memberLookup.myInfo().name
        return InteropAction.ServerResponse("$greeting -> Hello, my real name is $name")
    }

    override fun getBalance(greeting: String): InteropAction<String> {
        return InteropAction.ServerResponse("100")
    }
}
