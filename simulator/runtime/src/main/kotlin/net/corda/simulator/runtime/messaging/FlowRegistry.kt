package net.corda.simulator.runtime.messaging

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
     * Registers an instance of a responder flow for a given member and protocol
     *
     * @param member The member who initiates/ responds to the flow
     * @param protocol The protocol of the responding flow
     * @param responderFlow The instance flow class
     */
    fun registerResponderInstance(responder: MemberX500Name, protocol: String, responderFlow: ResponderFlow)


    /**
     * @param member The member for whom to look up the responder class.
     * @param protocol The protocol to which the responder should respond.
     *
     * @return A [ResponderFlow] class previously registered against the given name and protocol, or null if
     * no class has been registered.
     */
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?

    /**
     * @param member The member for whom to look up the responder instance.
     * @param protocol The protocol to which the responder should respond.
     *
     * @return A [ResponderFlow] instance previously registered against the given name and protocol, or null if
     * no instance has been registered.
     */
    fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow?
}