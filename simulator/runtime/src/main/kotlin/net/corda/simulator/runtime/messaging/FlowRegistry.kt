package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

interface FlowRegistry{

    /**
     * Registers a responder class against the given member name and protocol.
     *
     * @param responder The member for whom to register the responder class.
     * @param protocol The detected protocol of the responder class.
     * @param flowClass The responder class to construct for the given protocol.
     */
    fun registerResponderClass(responder: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>)

    /**
     * Registers an instance initiating flows for a given member and protocol
     *
     * @param member The member who initiates/ responds to the flow
     * @param protocol The protocol of the initiating flow
     * @param instanceFlow The instance flow class
     */
    fun registerFlowInstance(member: MemberX500Name, protocol: String, instanceFlow: Flow)


    /**
     * @param member The member for whom to look up the responder class.
     * @param protocol The protocol to which the responder should respond.
     *
     * @return A [ResponderFlow] class previously registered against the given name and protocol, or null if
     * no class has been registered.
     */
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?

    /**
     * @param member The member for whom to look up the initiator instance.
     * @param protocol The protocol for the initiator flow.
     *
     * @return A [Map] of previously registered instance initiating flows with protocols
     */
    fun lookUpInitiatorInstance(member: MemberX500Name): Map<RPCStartableFlow, String>?

    /**
     * @param member The member for whom to look up the responder instance.
     * @param protocol The protocol to which the responder should respond.
     *
     * @return A [ResponderFlow] instance previously registered against the given name and protocol, or null if
     * no instance has been registered.
     */
    fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow?

}