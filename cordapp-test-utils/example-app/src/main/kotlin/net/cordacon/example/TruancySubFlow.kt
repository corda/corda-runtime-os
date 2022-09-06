package net.cordacon.example

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

@InitiatingFlow("truancy-record")
class TruancySubFlow(
        private val truancyOffice: MemberX500Name,
        private val truancyRecord: TruancyRecord
    ) : SubFlow<Unit> {

    @CordaInject
    lateinit var flowMessaging : FlowMessaging

    @Suspendable
    override fun call() {
        val session = flowMessaging.initiateFlow(truancyOffice)
        session.send(truancyRecord)
    }
}
