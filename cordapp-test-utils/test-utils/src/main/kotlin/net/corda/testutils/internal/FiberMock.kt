package net.corda.testutils.internal

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

interface FiberMock {
    fun registerResponderClass(member: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>)
    fun registerResponderInstance(member: MemberX500Name, protocol: String, responder: ResponderFlow)
    fun registerPersistenceService(x500: MemberX500Name, persistence: PersistenceService)
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?
    fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow?
    fun getPersistenceService(x500: MemberX500Name): PersistenceService
}
