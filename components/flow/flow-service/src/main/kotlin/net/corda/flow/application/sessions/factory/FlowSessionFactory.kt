package net.corda.flow.application.sessions.factory

import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name

/**
 * [FlowSessionFactory] creates [FlowSession]s.
 */
interface FlowSessionFactory {
    /**
     * Creates a [FlowSession] which represents a session created in a flow by user code.
     *
     * @param sessionId The session id of the [FlowSession].
     * @param x500Name The X500 name of the counterparty the [FlowSession] interacts with.
     * @param flowContextPropertiesBuilder An optional builder of context properties
     * @param isInteropSession Optional flag to process the session as an interop session.
     *
     * @return A [FlowSession].
     */
    fun createInitiatingFlowSession(
        sessionId: String,
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder?,
        isInteropSession: Boolean = false
    ): FlowSession

    /**
     * Creates a [FlowSession] which represents a session passed to an initiated flow.
     *
     * @param sessionId The session id of the [FlowSession].
     * @param x500Name The X500 name of the counterparty the [FlowSession] interacts with.
     * @param contextProperties The context properties that should be attached to this flow session.
     * @param isInteropSession Optional flag to process the session as an interop session.
     *
     * @return A [FlowSession].
     */
    fun createInitiatedFlowSession(
        sessionId: String,
        x500Name: MemberX500Name,
        contextProperties: Map<String, String>,
        isInteropSession: Boolean = false
    ): FlowSession
}