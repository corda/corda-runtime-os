package net.corda.cordapptestutils.internal.messaging

import net.corda.cordapptestutils.internal.persistence.CloseablePersistenceService
import net.corda.cordapptestutils.internal.persistence.HsqlPersistenceServiceFactory
import net.corda.cordapptestutils.internal.persistence.PersistenceServiceFactory
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo

/**
 * Registers, and looks up, responder flows via their protocol.
 */
class SimFiberBase(
    private val persistenceServiceFactory : PersistenceServiceFactory = HsqlPersistenceServiceFactory(),
    private val memberLookUpFactory: MemberLookupFactory = BaseMemberLookupFactory()
) : SimFiber {

    private val nodeClasses = HashMap<MemberX500Name, HashMap<String, Class<out ResponderFlow>>>()
    private val nodeInstances = HashMap<MemberX500Name, HashMap<String, ResponderFlow>>()
    private val persistenceServices = HashMap<MemberX500Name, CloseablePersistenceService>()
    private val memberInfos = HashMap<MemberX500Name, MemberInfo>()

    override val members
        get() = memberInfos

    override fun registerInitiator(initiator: MemberX500Name) {
        registerMember(initiator)
    }

    private fun registerMember(member: MemberX500Name) {
        if (!memberInfos.contains(member)) {
            memberInfos[member] = BaseMemberInfo(member)
        }
    }

    override fun registerResponderClass(
        responder: MemberX500Name,
        protocol: String,
        flowClass: Class<out ResponderFlow>) {

        registerMember(responder)

        if(nodeInstances[responder]?.get(protocol) != null) {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }

        if(nodeClasses[responder] == null) {
            nodeClasses[responder] = hashMapOf(protocol to flowClass)
        } else if (nodeClasses[responder]!![protocol] == null) {
            nodeClasses[responder]!![protocol] = flowClass
        } else {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow class for protocol \"$protocol\"")
        }
    }

    override fun registerResponderInstance(
        responder: MemberX500Name,
        protocol: String,
        responderFlow: ResponderFlow) {

        registerMember(responder)

        if(nodeClasses[responder]?.get(protocol) != null) {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow class for protocol \"$protocol\"")
        }

        if(nodeInstances[responder] == null) {
            nodeInstances[responder] = hashMapOf(protocol to responderFlow)
        } else if (nodeInstances[responder]!![protocol] == null) {
            nodeInstances[responder]!![protocol] = responderFlow
        } else {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }
    }

    override fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow? {
        return nodeInstances[member]?.get(protocol)
    }

    override fun getOrCreatePersistenceService(member: MemberX500Name): PersistenceService {
        if (!persistenceServices.contains(member)) {
            persistenceServices[member] = persistenceServiceFactory.createPersistenceService(member)
        }
        return persistenceServices[member]!!
    }

    override fun createMemberLookup(member: MemberX500Name): MemberLookup {
        return memberLookUpFactory.createMemberLookup(member, this)
    }

    override fun close() {
        persistenceServices.values.forEach { it.close() }
    }

    override fun lookUpResponderClass(member: MemberX500Name, protocol: String) : Class<out ResponderFlow>? {
        return nodeClasses[member]?.get(protocol)
    }

}
