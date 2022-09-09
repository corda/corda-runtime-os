package net.corda.v5.application.flows

import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * A [SubFlow] is a type of [Flow] that should be started within another [Flow] in using with [FlowEngine.subFlow].
 *
 * [SubFlow]s can be used to:
 *
 * - Extract code from a [Flow] into smaller [SubFlow]s that can be reused.
 * - Initiate new counterparty [Flow]s when combined with [InitiatingFlow].
 *
 * Arguments can be passed into a [SubFlow] instance via its constructor or setter methods as there are no special
 * requirements around constructing a [SubFlow] instance.
 *
 * A [SubFlow] not annotated with [InitiatingFlow] cannot start new sessions of its own, but can have sessions created
 * by the [Flow] calling [FlowEngine.subFlow] passed into it (generally via a constructor) which it can interact with.
 * The lifecycles of these sessions are not bound to the [SubFlow] are are not tidied up upon leaving [SubFlow.call].
 *
 * > Note, that [SubFlow]s not annotated with [InitiatingFlow] are typically referred to as _inline_ [SubFlow]s.
 *
 * Annotating a [SubFlow] with [InitiatingFlow] allows it to initiate new sessions through [FlowMessaging] causing the
 * counterparty [Flow] with the matching protocol to be started. The lifecycles of these sessions are then bound to
 * the [SubFlow] and are tidied up upon leaving [SubFlow.call]. Sessions can still be passed into the [SubFlow] and
 * interacted with, but the lifecycles of these sessions are not bound to the [SubFlow].
 *
 * Example usage of a [SubFlow] not annotated with [InitiatingFlow]:
 *
 * - Kotlin:
 *
 * ```kotlin
 * class MySubFlow(private val existingSession: FlowSession) : SubFlow<String> {
 *
 *     @Suspendable
 *     override fun call(): String {
 *         return existingSession.receive<String>().unwrap { it }
 *     }
 * }
 * ```
 *
 * - Java:
 *
 * ```java
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
 * ```
 *
 * Example usage of a [SubFlow] annotated with [InitiatingFlow]:
 *
 * - Kotlin:
 *
 * ```kotlin
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
 *         val newSessionResult: String = newSession.sendAndReceive<String>("hello").unwrap { it }
 *         val existingSessionResult: String = existingSession.receive<String>().unwrap { it }
 *
 *         return newSessionResult + existingSessionResult
 *     }
 * }
 * ```
 *
 * - Java:
 *
 * ```java
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
 *         String newSessionResult = newSession.sendAndReceive(String.class, "hello").unwrap(it -> it);
 *         String existingSessionResult = existingSession.receive(String.class).unwrap(it -> it);
 *
 *         return newSessionResult + existingSessionResult;
 *     }
 * }
 * ```
 * @see FlowEngine.subFlow
 */
interface SubFlow<out T> : Flow {

    /**
     * This is where you fill out your business logic.
     *
     * @throws CordaRuntimeException General type of exception thrown by most Corda APIs.
     */
    @Suspendable
    fun call(): T
}