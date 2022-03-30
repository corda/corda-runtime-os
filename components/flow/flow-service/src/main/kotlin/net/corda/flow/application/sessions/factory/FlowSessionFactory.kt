package net.corda.flow.application.sessions.factory

import net.corda.v5.application.flows.FlowSession
import net.corda.v5.base.types.MemberX500Name

/**
 * [FlowSessionFactory] creates [FlowSession]s.
 */
interface FlowSessionFactory {

    /**
     * Creates a [FlowSession].
     *
     * @param sessionId The session id of the [FlowSession].
     * @param x500Name The X500 name of the counterparty the [FlowSession] interacts with.
     * @param initiated `true` if the [FlowSession] should be in state `initiated` upon creation, `false` for `uninitiated`.
     *
     * @return A [FlowSession].
     */
    fun create(sessionId: String, x500Name: MemberX500Name, initiated: Boolean): FlowSession
}