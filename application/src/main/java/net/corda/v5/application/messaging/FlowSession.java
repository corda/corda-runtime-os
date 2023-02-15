package net.corda.v5.application.messaging;

import net.corda.v5.application.flows.FlowContextProperties;
import net.corda.v5.application.flows.FlowEngine;
import net.corda.v5.application.flows.ResponderFlow;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link FlowSession} is a handle on a communication sequence between two paired flows, possibly running on separate nodes.
 * <p>
 * It is used to send and receive messages between the flows as well as to query information about the counter-flow.
 * Sessions have their own local flow context which can be accessed via {@link #getContextProperties}. Note that the
 * parent context is snapshotted at the point {@link #getContextProperties} is first accessed, after which no other changes to
 * the parent context will be reflected in them. See {@link #getContextProperties} for more information.
 * <p>
 * There are two ways of obtaining such a session:
 * <ol><li>Calling {@link FlowMessaging#initiateFlow}. This will create a {@link FlowSession} object on which the first send/receive
 * operation will attempt to kick off a corresponding {@link ResponderFlow} flow on the counterpart's node.</li>
 * <li>As constructor parameter to {@link ResponderFlow} flows. This session is the one corresponding to the initiating flow
 * and may be used for replies.</li></ol>
 *
 * @see FlowMessaging
 */
@DoNotImplement
public interface FlowSession {

    /**
     * The {@link MemberX500Name} of the counterparty this session is connected to.
     */
    @NotNull
    MemberX500Name getCounterparty();

    /**
     * Session local {@link FlowContextProperties}.
     * <p>
     * If this session is part of an initiating flow, i.e. was obtained from {@link FlowMessaging} then this is a read only
     * set of context properties which will be used to determine context on the initiated side. Modifying this set is
     * only possible when session are initiated, see {@link FlowMessaging}.
     * <p>
     * If this session was passed to an initiated flow by Corda, the context properties are associated with the
     * initiating flow at the other end of the connection. They differ from the {@link FlowContextProperties} available to all
     * flows via the {@link FlowEngine} in that they are not a description of the context of the currently executing flow, but
     * instead the flow which initiated it.
     * <p>
     * Any calls to modify these contextProperties will throw a {@link CordaRuntimeException}, they should be considered
     * immutable.
     */
    @NotNull
    FlowContextProperties getContextProperties();

    /**
     * Serializes and queues the given {@code payload} object for sending to {@link #getCounterparty}. Suspends until a response is
     * received, which must be of the given {@code receiveType}.
     * <p>
     * Note that this function is not just a simple send and receive pair. It is more efficient and more correct to use
     * sendAndReceive when you expect to do a message swap rather than use {@link FlowSession#send} and then
     * {@link FlowSession#receive}.
     *
     * @param <R> The data type received from the counterparty.
     * @param receiveType The data type received from the counterparty.
     * @param payload The data sent to the counterparty.
     *
     * @return The received data <R>
     *
     * @throws CordaRuntimeException if the session is closed or in a failed state.
     */
    @Suspendable
    @NotNull
    <R> R sendAndReceive(@NotNull Class<R> receiveType, @NotNull Object payload);


    /**
     * Suspends until a message of type <R> is received from {@link #getCounterparty}.
     *
     * @param <R> The data type received from the counterparty.
     * @param receiveType The data type received from the counterparty.
     *
     * @return The received data <R>
     *
     * @throws CordaRuntimeException if the session is closed or in a failed state.
     */
    @Suspendable
    @NotNull
    <R> R receive(@NotNull Class<R> receiveType);


    /**
     * Queues the given {@code payload} for sending to {@link #getCounterparty} and continues without suspending.
     * <p>
     * Note that the other party may receive the message at some arbitrary later point or not at all: if {@link #getCounterparty}
     * is offline then message delivery will be retried until it comes back or until the message is older than the
     * network's event horizon time.
     *
     * @param payload The data sent to the counterparty.
     *
     * @throws CordaRuntimeException if the session is closed or in a failed state.
     */
    @Suspendable
    void send(@NotNull Object payload);

    /**
     * Closes this session and performs cleanup of any resources tied to this session.
     * <p>
     * Note that sessions are closed automatically when the corresponding top-level flow terminates.
     * <p>
     * So, it's beneficial to eagerly close them in long-lived flows that might have many open sessions that are not
     * needed anymore and consume resources (e.g. memory, disk etc.).
     * <p>
     * A closed session cannot be used anymore, e.g. to send or receive messages. So, you have to ensure you are calling
     * this method only when the session is not going to be used anymore. As a result, any operations on a closed
     * session will fail with an {@link CordaRuntimeException}.
     * <p>
     * When a session is closed, the other side is informed and the session is closed there too eventually.
     */
    @Suspendable
    void close();
}
