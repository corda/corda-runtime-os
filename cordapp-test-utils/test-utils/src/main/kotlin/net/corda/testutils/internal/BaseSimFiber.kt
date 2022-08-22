package net.corda.testutils.internal

import net.corda.testutils.services.CloseablePersistenceService
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name

/**
 * Registers, and looks up, responder flows via their protocol.
 */
class BaseSimFiber(private val persistenceServiceFactory : PersistenceServiceFactory = HsqlPersistenceServiceFactory())
    : SimFiber {

    private val nodeClasses = HashMap<MemberX500Name, HashMap<String, Class<out ResponderFlow>>>()
    private val nodeInstances = HashMap<MemberX500Name, HashMap<String, ResponderFlow>>()
    private val persistenceServices = HashMap<MemberX500Name, CloseablePersistenceService>()

    override fun registerResponderClass(responder: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>) {
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

    override fun registerResponderInstance(responder: MemberX500Name, protocol: String, responderFlow: ResponderFlow) {
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

    override fun close() {
        persistenceServices.values.forEach { it.close() }
    }

    override fun lookUpResponderClass(member: MemberX500Name, protocol: String) : Class<out ResponderFlow>? {
        return nodeClasses[member]?.get(protocol)
    }

}
