package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap

class BaseFlowRegistry: FlowRegistry {
    private val nodeFlowInstances = ConcurrentHashMap<MemberX500Name, ConcurrentHashMap<Flow, String>>()
    private val nodeResponderClasses = ConcurrentHashMap<MemberX500Name,
            ConcurrentHashMap<String, Class<out ResponderFlow>>>()
    private val nodeResponderInstances = ConcurrentHashMap<MemberX500Name, ConcurrentHashMap<String, ResponderFlow>>()

    private companion object {
        val log = contextLogger()
    }

    override fun registerResponderClass(
        responder: MemberX500Name,
        protocol: String,
        flowClass: Class<out ResponderFlow>
    ) {
        if(nodeResponderInstances[responder]?.get(protocol) != null) {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }

        log.info("Registering class $flowClass against protocol $protocol")

        nodeResponderClasses.putIfAbsent(responder, ConcurrentHashMap())
        val alreadyRegistered = nodeResponderClasses[responder]!!.putIfAbsent(protocol, flowClass)

        if(alreadyRegistered != null) {
            error("Member \"$responder\" has already registered flow class for protocol \"$protocol\"")
        }
    }

    override fun registerResponderInstance(responder: MemberX500Name, protocol: String, responderFlow: ResponderFlow) {

        if(nodeResponderClasses[responder]?.get(protocol) != null) {
            error("Member \"$responder\" has already registered flow class for protocol \"$protocol\"")
        }

        log.info("Registering instance $responderFlow against protocol $protocol")

        nodeResponderInstances.putIfAbsent(responder, ConcurrentHashMap())
        val alreadyRegistered = nodeResponderInstances[responder]!!.putIfAbsent(protocol, responderFlow)

        if(alreadyRegistered != null) {
            error("Member \"$responder\" has already registered flow instance for protocol \"$protocol\"")
        }
    }

    override fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>? {
        return nodeResponderClasses[member]?.get(protocol)
    }

    override fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow? {
        return nodeResponderInstances[member]?.get(protocol)
    }
}