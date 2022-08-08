package net.corda.testutils.internal

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

interface FlowFactory {
    fun createInitiatingFlow(x500: MemberX500Name, flowClassName: String): RPCStartableFlow
    fun createResponderFlow(x500: MemberX500Name, flowClass: Class<out Flow>): ResponderFlow
}

