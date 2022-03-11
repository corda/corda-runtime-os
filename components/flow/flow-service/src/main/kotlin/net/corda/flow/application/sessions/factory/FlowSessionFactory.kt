package net.corda.flow.application.sessions.factory

import net.corda.v5.application.flows.FlowSession
import net.corda.v5.base.types.MemberX500Name

interface FlowSessionFactory {

    fun create(sessionId: String, x500Name: MemberX500Name, initiated: Boolean): FlowSession
}