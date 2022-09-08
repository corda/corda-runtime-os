package net.corda.v5.application.messaging

import net.corda.v5.application.flows.FlowContextProperties

/**
 * Builder of context properties. Instances of this interface can optionally be passed to [FlowMessaging] when sessions
 * are initiated if there are requirements to modify context properties which are sent to the initiated flow.
 */
fun interface FlowContextPropertiesBuilder {
    /**
     * Every exception thrown in the implementation of this method will be propagated back through the [FlowMessaging]
     * call to initiate the session. It is recommended not to do anything in this method except set context properties.
     *
     * @param flowContextProperties A set of modifiable context properties. Change these properties in the body of the
     * function as required. The modified set will be the ones used to determine the context of the initiated session.
     */
    fun apply(flowContextProperties: FlowContextProperties)
}