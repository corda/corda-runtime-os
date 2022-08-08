package net.corda.testutils.internal

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

interface FiberMock : ProtocolLookUp {
    fun registerResponderClass(member: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>)
    fun registerResponderInstance(member: MemberX500Name, protocol: String, responder: ResponderFlow)
}

interface ProtocolLookUp {
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?
    fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow?
}
