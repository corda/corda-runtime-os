package net.corda.testutils.internal

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

/**
 * Registers, and looks up, responder flows via their protocol.
 */
class BaseFiberMock : FiberMock {

    private val nodeClasses = HashMap<MemberX500Name, HashMap<String, Class<out ResponderFlow>>>()
    private val nodeInstances = HashMap<MemberX500Name, HashMap<String, ResponderFlow>>()
    private val persistenceServices = HashMap<MemberX500Name, PersistenceService>()

    override fun registerResponderClass(member: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>) {
        if(nodeInstances[member]?.get(protocol) != null) {
            throw IllegalStateException("Member \"$member\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }

        if(nodeClasses[member] == null) {
            nodeClasses[member] = hashMapOf(protocol to flowClass)
        } else if (nodeClasses[member]!![protocol] == null) {
            nodeClasses[member]!![protocol] = flowClass
        } else {
            throw IllegalStateException("Member \"$member\" has already registered " +
                    "flow class for protocol \"$protocol\"")
        }
    }

    override fun registerResponderInstance(member: MemberX500Name, protocol: String, responder: ResponderFlow) {
        if(nodeClasses[member]?.get(protocol) != null) {
            throw IllegalStateException("Member \"$member\" has already registered " +
                    "flow class for protocol \"$protocol\"")
        }

        if(nodeInstances[member] == null) {
            nodeInstances[member] = hashMapOf(protocol to responder)
        } else if (nodeInstances[member]!![protocol] == null) {
            nodeInstances[member]!![protocol] = responder
        } else {
            throw IllegalStateException("Member \"$member\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }
    }

    override fun registerPersistenceService(x500: MemberX500Name, persistence: PersistenceService) {
        persistenceServices[x500] = persistence
    }

    override fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow? {
        return nodeInstances[member]?.get(protocol)
    }

    override fun getPersistenceService(x500: MemberX500Name): PersistenceService {
        return persistenceServices[x500] ?:
            throw IllegalArgumentException("No persistence service created for member $x500")
    }

    override fun lookUpResponderClass(member: MemberX500Name, protocol: String) : Class<out ResponderFlow>? {
        return nodeClasses[member]?.get(protocol)
    }

}
