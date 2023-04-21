package net.cordacon.example.rollcall

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "truancy-record")
class TruancySubFlow(
        private val truancyOffice: MemberX500Name,
        private val truancyRecord: TruancyRecord
    ) : SubFlow<String> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


    @CordaInject
    lateinit var flowMessaging : FlowMessaging

    @Suspendable
    override fun call(): String {
        log.info("Sending truancy record")
        val session = flowMessaging.initiateFlow(truancyOffice)
        session.send(truancyRecord)
        return ""
    }
}
