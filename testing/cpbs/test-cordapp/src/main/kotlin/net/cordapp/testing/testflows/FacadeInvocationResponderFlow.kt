package net.cordapp.testing.testflows

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

@InitiatedBy(protocol = "invoke_facade_method")
class FacadeInvocationResponderFlow : ResponderFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @Suspendable
    override fun call(session: FlowSession) {
        log.info("FacadeInvocationResponderFlow.call() starting")
        val request = session.receive(String::class.java)
        val response = "$request:Bye"
        log.info("FacadeInvocationResponderFlow.call(): received=$request, response=$response")
        session.send(response)
    }
}