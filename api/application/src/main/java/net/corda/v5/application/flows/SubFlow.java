package net.corda.v5.application.flows;

import net.corda.v5.application.messaging.FlowMessaging;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.exceptions.CordaRuntimeException;

/**
 * A {@link SubFlow} can be used to:
 * <ul>
 * <li>Extract code from a {@link Flow} into smaller {@link SubFlow}s that can be reused.</li>
 * <li>Initiate new counterparty {@link Flow}s when combined with {@link InitiatingFlow}.</li>
 * </ul>
 *
 * {@link SubFlow}s are executed by calling {@link FlowEngine#subFlow} and passing a {@link SubFlow} instance into the method.
 * <p>
 * Arguments can be passed into a {@link SubFlow} instance via its constructor or setter methods as there are no special
 * requirements around constructing a {@link SubFlow} instance.
 * <p>
 * A {@link SubFlow} not annotated with {@link InitiatingFlow} cannot start new sessions of its own, but can have sessions created
 * by the {@link Flow} calling {@link FlowEngine#subFlow} passed into it (generally via a constructor) which it can interact with.
 * The lifecycles of these sessions are not bound to the {@link SubFlow} instance and are not tidied up upon leaving
 * {@link SubFlow#call}.
 * <p>
 * Note, that {@link SubFlow}s not annotated with {@link InitiatingFlow} are typically referred to as inline {@link SubFlow}s.
 * <p>
 * Annotating a {@link SubFlow} with {@link InitiatingFlow} allows it to initiate new sessions through {@link FlowMessaging} causing the
 * counterparty {@link Flow} with the matching protocol to be started. The lifecycles of these sessions are then bound to
 * the {@link SubFlow} and are tidied up upon leaving {@link SubFlow#call}. Sessions can still be passed into the {@link SubFlow} and
 * interacted with, but the lifecycles of these sessions are not bound to the {@link SubFlow} instance.
 * <p>
 * Example usage of a {@link SubFlow} not annotated with {@link InitiatingFlow}:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * class MySubFlow(private val existingSession: FlowSession) : SubFlow<String> {
 *
 *     @Suspendable
 *     override fun call(): String {
 *         return existingSession.receive<String>().unwrap { it }
 *     }
 * }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class MySubFlow implements SubFlow<String> {
 *
 *     private final FlowSession existingSession;
 *
 *     public MySubFlow(FlowSession existingSession) {
 *         this.existingSession = existingSession;
 *     }
 *
 *     @Suspendable
 *     @Override
 *     public String call() {
 *         return existingSession.receive(String.class).unwrap(it -> it);
 *     }
 * }
 * }</pre></li></ul>
 *
 * Example usage of a {@link SubFlow} annotated with {@link InitiatingFlow}:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * @InitiatingFlow("protocol")
 * class MySubFlow(private val existingSession: FlowSession, private val x500Name: MemberX500Name) : SubFlow<String> {
 *
 *     @CordaInject
 *     lateinit var flowMessaging: FlowMessaging
 *
 *     @Suspendable
 *     override fun call(): String {
 *         val newSession: FlowSession = flowMessaging.initiateFlow(x500Name)
 *
 *         val newSessionResult: String = newSession.sendAndReceive<String>("hello")
 *         val existingSessionResult: String = existingSession.receive<String>()
 *
 *         return newSessionResult + existingSessionResult
 *     }
 * }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class MySubFlow implements SubFlow<String> {
 *
 *     @CordaInject
 *     public FlowMessaging flowMessaging;
 *
 *     private final FlowSession existingSession;
 *     private final MemberX500Name x500Name;
 *
 *     public MySubFlow(FlowSession existingSession, MemberX500Name x500Name) {
 *         this.existingSession = existingSession;
 *         this.x500Name = x500Name;
 *     }
 *
 *     @Suspendable
 *     @Override
 *     public String call() {
 *         FlowSession newSession = flowMessaging.initiateFlow(x500Name);
 *
 *         String newSessionResult = newSession.sendAndReceive(String.class, "hello");
 *         String existingSessionResult = existingSession.receive(String.class);
 *
 *         return newSessionResult + existingSessionResult;
 *     }
 * }
 * }</pre></li>
 * </ul>
 * @see FlowEngine#subFlow
 */
public interface SubFlow<T> extends Flow {

    /**
     * This is where you fill out your business logic.
     *
     * @throws CordaRuntimeException General type of exception thrown by most Corda APIs.
     */
    @Suspendable
    T call();
}
