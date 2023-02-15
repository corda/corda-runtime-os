package net.corda.v5.application.flows;

import net.corda.v5.application.messaging.FlowMessaging;
import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.base.annotations.Suspendable;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ResponderFlow} is a {@link Flow} that is started by receiving a message from a peer.
 * <p>
 * A {@link ResponderFlow} must be annotated with {@link InitiatedBy} to be invoked by a session message. If both
 * these requirements are met, then the flow will be invoked via {@link ResponderFlow#call} which takes a {@link FlowSession}. This
 * session is created by the platform and communicates with the party that initiated the session.
 * <p>
 * Flows implementing this interface must have a no-arg constructor. The flow invocation will fail if this constructor
 * does not exist.
 *
 * @see InitiatedBy
 */
public interface ResponderFlow extends Flow {

    /**
     * The business logic for the flow should be written here.
     * <p>
     * This is equivalent to the call method for a normal flow. This version is invoked when the flow is started via an
     * incoming session init event, via a counterparty calling {@link FlowMessaging#initiateFlow}.
     *
     * @param session The session opened by the counterparty.
     */
    @Suspendable
    void call(@NotNull FlowSession session);
}
