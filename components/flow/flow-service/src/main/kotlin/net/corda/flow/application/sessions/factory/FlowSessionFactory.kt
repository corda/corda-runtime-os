package net.corda.flow.application.sessions.factory

import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import java.time.Duration

/**
 * [FlowSessionFactory] creates [FlowSession]s.
 */
interface FlowSessionFactory {
    /**
     * Creates a [FlowSession] which represents a session created in a flow by user code.
     *
     * @param sessionId The session id of the [FlowSession].
     * @param requireClose True if the initiated party sends a close message when a session is closed.
     * @param sessionTimeout Session timeout.
     * @param x500Name The X500 name of the counterparty the [FlowSession] interacts with.
     * @param flowContextPropertiesBuilder An optional builder of context properties
     *
     * @return A [FlowSession].
     */
    fun createInitiatingFlowSession(
        sessionId: String,
        requireClose: Boolean,
        sessionTimeout: Duration?,
        x500Name: MemberX500Name,
        flowContextPropertiesBuilder: FlowContextPropertiesBuilder?
    ): FlowSession
    
    /**
     * Creates a [FlowSession] which represents a session passed to an initiated flow.
     *
     * @param sessionId The session id of the [FlowSession].
     * @param requireClose True if the initiated party sends a close message when a session is closed.
     * @param sessionTimeout Session timeout.
     * @param x500Name The X500 name of the counterparty the [FlowSession] interacts with.
     * @param contextProperties The context properties that should be attached to this flow session.
     *
     * @return A [FlowSession].
     */
    fun createInitiatedFlowSession(
        sessionId: String,
        requireClose: Boolean,
        sessionTimeout: Duration?,
        x500Name: MemberX500Name,
        contextProperties: Map<String, String>
    ): FlowSession
}