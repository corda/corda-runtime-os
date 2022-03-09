package net.corda.application.internal.flow.session

import net.corda.v5.application.flows.FlowSession
import net.corda.v5.base.types.MemberX500Name

interface FlowSessionFactory {

    fun create(sessionId: String, x500Name: MemberX500Name, initiated: Boolean): FlowSession
}