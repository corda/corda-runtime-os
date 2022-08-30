package net.corda.cordapptestutils.internal.messaging

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import java.io.Closeable

interface SimFiber : Closeable {
    fun registerResponderClass(responder: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>)
    fun registerResponderInstance(responder: MemberX500Name, protocol: String, responderFlow: ResponderFlow)
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?
    fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow?
    fun getOrCreatePersistenceService(member: MemberX500Name): PersistenceService
}
