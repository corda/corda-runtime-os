package net.corda.cordapptestutils.internal.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

interface FlowFactory {
    fun createInitiatingFlow(member: MemberX500Name, flowClassName: String): RPCStartableFlow
    fun createResponderFlow(member: MemberX500Name, flowClass: Class<out Flow>): ResponderFlow
}

