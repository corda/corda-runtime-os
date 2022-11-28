package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

class BaseFlowRegistry: FlowRegistry {
    private val nodeInitiatorInstances = HashMap<MemberX500Name, HashMap<RPCStartableFlow, String>>()
    private val nodeClasses = HashMap<MemberX500Name, HashMap<String, Class<out ResponderFlow>>>()
    private val nodeInstances = HashMap<MemberX500Name, HashMap<String, ResponderFlow>>()

    override fun registerResponderClass(
        responder: MemberX500Name,
        protocol: String,
        flowClass: Class<out ResponderFlow>
    ) {
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

    private fun registerInitiatorInstance(
        initiator: MemberX500Name,
        protocol: String,
        initatingFlow: RPCStartableFlow
    ) {
        if(!nodeInitiatorInstances.contains(initiator)) {
            nodeInitiatorInstances[initiator] = hashMapOf(initatingFlow to protocol)
        }else if(nodeInitiatorInstances[initiator]!![initatingFlow] == null){
            nodeInitiatorInstances[initiator]!![initatingFlow] = protocol
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

    override fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>? {
        return nodeClasses[member]?.get(protocol)
    }

    override fun lookUpInitiatorInstance(member: MemberX500Name): Map<RPCStartableFlow, String>? {
        return nodeInitiatorInstances[member]
    }

    override fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow? {
        return nodeInstances[member]?.get(protocol)
    }
}