package net.corda.flow.fiber

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

/**
 * Represents a flow started via a session init event.
 */
class InitiatedFlow(override val logic: ResponderFlow, private val session: FlowSession) : FlowLogicAndArgs {

    @Suspendable
    override fun invoke() : String? {
        logic.call(session)
        return null
    }
}