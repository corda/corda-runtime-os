package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

class BaseFlowRegistry: FlowRegistry {
    private val nodeInitiatorInstances = HashMap<MemberX500Name, HashMap<RPCStartableFlow, String>>()
    private val nodeResponderClasses = HashMap<MemberX500Name, HashMap<String, Class<out ResponderFlow>>>()
    private val nodeResponderInstances = HashMap<MemberX500Name, HashMap<String, ResponderFlow>>()

    override fun registerResponderClass(
        responder: MemberX500Name,
        protocol: String,
        flowClass: Class<out ResponderFlow>
    ) {
        if(nodeResponderInstances[responder]?.get(protocol) != null) {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }

        if(nodeResponderClasses[responder] == null) {
            nodeResponderClasses[responder] = hashMapOf(protocol to flowClass)
        } else if (nodeResponderClasses[responder]!![protocol] == null) {
            nodeResponderClasses[responder]!![protocol] = flowClass
        } else {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow class for protocol \"$protocol\"")
        }
    }

    private fun registerInitiatorInstance(
        initiator: MemberX500Name,
        protocol: String,
        initiatingFlow: RPCStartableFlow
    ) {
        if(!nodeInitiatorInstances.contains(initiator)) {
            nodeInitiatorInstances[initiator] = hashMapOf(initiatingFlow to protocol)
        }else if(nodeInitiatorInstances[initiator]!![initiatingFlow] == null){
            nodeInitiatorInstances[initiator]!![initiatingFlow] = protocol
        }else{
            throw IllegalStateException("Member \"$initiator\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }
    }

    override fun registerFlowInstance(member: MemberX500Name, protocol: String, instanceFlow: Flow) {
        if(instanceFlow is ResponderFlow) {
            registerResponderInstance(member, protocol, instanceFlow)
        }else if(instanceFlow is RPCStartableFlow){
            registerInitiatorInstance(member, protocol, instanceFlow)
        }else {
            "$instanceFlow is neither a  ${RPCStartableFlow::class.java}" +
                    "nor a ${ResponderFlow::class.java}"
        }
    }


    private fun registerResponderInstance(responder: MemberX500Name, protocol: String, responderFlow: ResponderFlow) {

        if(nodeResponderClasses[responder]?.get(protocol) != null) {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow class for protocol \"$protocol\"")
        }

        if(nodeResponderInstances[responder] == null) {
            nodeResponderInstances[responder] = hashMapOf(protocol to responderFlow)
        } else if (nodeResponderInstances[responder]!![protocol] == null) {
            nodeResponderInstances[responder]!![protocol] = responderFlow
        } else {
            throw IllegalStateException("Member \"$responder\" has already registered " +
                    "flow instance for protocol \"$protocol\"")
        }
    }

    override fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>? {
        return nodeResponderClasses[member]?.get(protocol)
    }

    override fun lookUpInitiatorInstance(member: MemberX500Name): Map<RPCStartableFlow, String>? {
        return nodeInitiatorInstances[member]
    }

    override fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow? {
        return nodeResponderInstances[member]?.get(protocol)
    }
}