package net.corda.v5.application.messaging;

import net.corda.v5.application.flows.FlowContextProperties;
import org.jetbrains.annotations.NotNull;

/**
 * Builder of context properties. If required, instances of this interface can be passed to FlowMessaging when sessions
 * are initiated to modify context properties which are sent to the initiated flow.
 */
@FunctionalInterface
public interface FlowContextPropertiesBuilder {
    /**
     * Every exception thrown in the implementation of this method will be propagated back through the {@link FlowMessaging}
     * call to initiate the session. It is recommended not to do anything in this method except set context properties.
     *
     * @param flowContextProperties A set of modifiable context properties. Change these properties in the body of the
     * function as required. The modified set will be the ones used to determine the context of the initiated session.
     */
    void apply(@NotNull FlowContextProperties flowContextProperties);
}
