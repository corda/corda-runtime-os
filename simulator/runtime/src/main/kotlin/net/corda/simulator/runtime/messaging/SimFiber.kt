package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import java.io.Closeable
import java.security.PublicKey

interface SimFiber : Closeable, HasMemberInfos {
    fun registerInitiator(initiator: MemberX500Name)
    fun registerResponderClass(responder: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>)
    fun registerResponderInstance(responder: MemberX500Name, protocol: String, responderFlow: ResponderFlow)
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?
    fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow?
    fun getOrCreatePersistenceService(member: MemberX500Name): PersistenceService
    fun createMemberLookup(member: MemberX500Name): MemberLookup
    fun registerKey(member: MemberX500Name, publicKey: PublicKey)
}

interface HasMemberInfos {
    val members: Map<MemberX500Name, MemberInfo>
}
