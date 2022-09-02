package net.corda.cordapptestutils.internal.testflows

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

class ValidResponderFlow : ResponderFlow {
    @Suspendable
    override fun call(session: FlowSession) = Unit
}