package net.corda.simulator.runtime.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

/**
 * Creates initiating or responding flows while handing any errors.
 */
interface FlowFactory {

    /**
     * Creates an initiating flow from the given class name.
     *
     * @param member The member to create the flow for.
     * @param flowClassName The name of the flow class to create.
     * @return An instance of the given flow class.
     *
     * @throws [net.corda.simulator.exceptions.UnrecognizedFlowClassException] if the class does not extend
     * [RestStartableFlow].
     */
    fun createInitiatingFlow(member: MemberX500Name, flowClassName: String): RestStartableFlow

    /**
     * Creates a responding flow from the given class name.
     *
     * @param member The member to create the flow for.
     * @param flowClass The flow class to create.
     * @return An instance of the given flow class.
     *
     * @throws [net.corda.simulator.exceptions.UnrecognizedFlowClassException] if the class does not extend
     * [ResponderFlow].
     */
    fun createResponderFlow(member: MemberX500Name, flowClass: Class<out Flow>): ResponderFlow
}

