package net.cordapp.testing.testflows

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import org.slf4j.LoggerFactory

@InitiatedBy(protocol = "invoke_facade_method")
class FacadeInvocationResponderFlow : ResponderFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    override fun call(session: FlowSession) {
        log.info("FacadeInvocationResponderFlow.call() starting")
        val request = session.receive(Object::class.java)
        session.send(request)
    }
}