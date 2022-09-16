package net.corda.v5.application.flows

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * [InitiatedBy] specifies the protocol name that triggers a [ResponderFlow] as a consequence of a counterparty
 * requesting a new session.
 *
 * Any flows that participate in flow sessions must declare a protocol name, using [InitiatingFlow.protocol]
 * and [InitiatedBy.protocol]. The platform will use the protocol name to establish what [ResponderFlow] to invoke on
 * the responder side when the initiator side creates a session.
 *
 * For example, to set up an initiator-responder pair, declare the following:
 *
 * ```kotlin
 * @InitiatingFlow(protocol = "myprotocol")
 * class MyFlowInitiator : Flow {
 *  ...
 * }
 *
 * @InitiatedBy(protocol = "myprotocol")
 * class MyFlowResponder : ResponderFlow {
 *  ...
 * }
 * ```
 *
 * Flows may also optionally declare a range of protocol versions they support. By default, flows support protocol
 * version 1 only. When initiating a flow, the platform will look for the highest supported protocol version as declared
 * on the initiating side and start that flow on the responder side.
 *
 * @property protocol The protocol that the annotated flow is initiated by.
 * @property version The protocol version.
 *
 * @see InitiatingFlow
 * @see ResponderFlow
 */
@Target(CLASS)
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class InitiatedBy(val protocol: String, val version: IntArray = [1])