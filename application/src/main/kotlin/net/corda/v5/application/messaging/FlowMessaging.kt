package net.corda.v5.application.messaging

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowContextProperties.Companion.CORDA_RESERVED_PREFIX
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

/*
 * [FlowMessaging] allows a flow to initiate and communicate with one or more 3rd party flows.
 *
 * The platform will provide an instance of [FlowMessaging] to flows via property injection.
 *
 * A [Flow] can initiate one or more flows other counterparties within the network, when a new flow is initiated a
 * [FlowSession] is created. The [FlowSession] represents the connection to an initiated flow and can be used to send
 * and receive data between the two flows.
 *
 * Example usage:
 *
 * - Kotlin:
 *
 * ```kotlin
 * class MyFlow : RestStartableFlow {
 *    @CordaInject
 *    lateinit var flowMessaging: FlowMessaging
 *
 *    override fun call(requestBody: RestRequestBody): String {
 *        val counterparty = parse("CN=Alice, O=Alice Corp, L=LDN, C=GB")
 *
 *        val session = flowMessaging.initiateFlow(counterparty)
 *
 *        val result = session.sendAndReceive<String>("hello")
 *
 *        session.close()
 *
 *        return result
 *    }
 *  }
 * ```
 *
 * - Java:
 *
 * ```java
 * class MyFlow implements RestStartableFlow {
 *
 *    @CordaInject
 *    public FlowMessaging flowMessaging;
 *
 *    @Override
 *    public String call(RestRequestBody requestBody) {
 *        MemberX500Name counterparty = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB");
 *        FlowSession session = flowMessaging.initiateFlow(counterparty);
 *
 *        String result = session.sendAndReceive(String.class, "hello");
 *
 *        session.close();
 *
 *        return result;
 *    }
 *}
 * ```
 */
@DoNotImplement
interface FlowMessaging {

    /**
     * Creates a communication session with a counterparty's [ResponderFlow]. Subsequently, you may send/receive using
     * this session object. Note that this function does not communicate in itself. The counter-flow will be kicked off
     * by the first send/receive.
     *
     * Initiated flows are initiated with context based on the context of the initiating flow at the point in time this
     * method is called. The context of the initiating flow is snapshotted by the returned session. Altering the flow
     * context has no effect on the context of the session after this point, and therefore it has no effect on the
     * context of the initiated flow either.
     *
     * @param x500Name The X500 name of the member to communicate with.
     *
     * @return The session.
     */
    @Suspendable
    fun initiateFlow(x500Name: MemberX500Name): FlowSession

    /**
     * Creates a communication session with another member. Subsequently you may send/receive using this session object.
     * Note that this function does not communicate in itself. The counter-flow will be kicked off by the first
     * send/receive.
     *
     * This overload takes a builder of context properties. Any properties set or modified against the context passed to
     * this builder will be propagated to initiated flows and all that flow's initiated flows and sub flows down the
     * stack. The properties passed to the builder are pre-populated with the current flow context properties, see
     * [FlowContextProperties]. Altering the current flow context has no effect on the context of the session after the
     * builder is applied and the session returned by this method, and therefore it has no effect on the context of the
     * initiated flow either.
     *
     * Example of use in Kotlin.
     * ```Kotlin
     * val flowSession = flowMessaging.initiateFlow(virtualNodeName) { flowContextProperties ->
     *      flowContextProperties["key"] = "value"
     * }
     * ```
     * Example of use in Java.
     * ```Java
     * FlowSession flowSession = flowMessaging.initiateFlow(virtualNodeName, (flowContextProperties) -> {
     *      flowContextProperties.put("key", "value");
     * });
     * ```
     *
     * @param x500Name The X500 name of the member to communicate with.
     * @param flowContextPropertiesBuilder A builder of context properties.
     *
     * @return The session.
     *
     * @throws IllegalArgumentException if the builder tries to set a property for which a platform property already
     * exists or if the key is prefixed by [CORDA_RESERVED_PREFIX]. See also [FlowContextProperties]. Any other
     * exception thrown by the builder will also be thrown through here and should be avoided in the provided
     * implementation, see [FlowContextPropertiesBuilder].
     */
    @Suspendable
    fun initiateFlow(x500Name: MemberX500Name, flowContextPropertiesBuilder: FlowContextPropertiesBuilder): FlowSession

    /**
     * Suspends until a message has been received for each session in the specified [sessions].
     *
     * Consider [receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, Any>] when sessions are
     * expected to receive different types.
     *
     * @param receiveType type of object to be received for all [sessions].
     * @param sessions Set of sessions to receive from.
     * @returns a [List] containing the objects received from the [sessions].
     *
     * @throws [CordaRuntimeException] if any session is closed or in a failed state.
     */
    @Suspendable
    fun <R : Any> receiveAll(receiveType: Class<out R>, sessions: Set<FlowSession>): List<R>

    /**
     * Suspends until a message has been received for each session in the specified [sessions].
     *
     * Consider [receiveAll(receiveType: Class<R>, sessions: Set<FlowSession>): List<R>] when the same type is expected from all sessions.
     *
     * @param sessions Map of session to the type of object that is expected to be received
     * @returns a [Map] containing the objects received by the [FlowSession]s who sent them.
     *
     * @throws [CordaRuntimeException] if any session is closed or in a failed state.
     */
    @Suspendable
    fun receiveAllMap(sessions: Map<FlowSession, Class<out Any>>): Map<FlowSession, Any>

    /**
     * Queues the given [payload] for sending to the provided [sessions] and continues without waiting for a response.
     *
     * Note that the other parties may receive the message at some arbitrary later point or not at all: if one of the provided [sessions]
     * is offline then message delivery will be retried until the session expires. Sessions are deemed to be expired when this session
     * stops receiving heartbeat messages from the counterparty within the configurable timeout.
     *
     * @param payload the payload to send.
     * @param sessions the sessions to send the provided payload to.
     *
     * @throws [CordaRuntimeException] if any session is closed or in a failed state.
     */
    @Suspendable
    fun sendAll(payload: Any, sessions: Set<FlowSession>)

    /**
     * Queues the given payloads for sending to the provided sessions and continues without waiting for a response.
     *
     * Note that the other parties may receive the message at some arbitrary later point or not at all: if one of the provided [sessions]
     * is offline then message delivery will be retried until the session expires. Sessions are deemed to be expired when this session
     * stops receiving heartbeat messages from the counterparty within the configurable timeout.
     *
     * @param payloadsPerSession a mapping that contains the payload to be sent to each session.
     *
     * @throws [CordaRuntimeException] if any session is closed or in a failed state.
     */
    @Suspendable
    fun sendAllMap(payloadsPerSession: Map<FlowSession, Any>)
}
