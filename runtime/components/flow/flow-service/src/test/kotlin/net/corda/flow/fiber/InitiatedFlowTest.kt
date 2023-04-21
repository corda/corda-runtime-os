package net.corda.flow.fiber

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class InitiatedFlowTest {

    private companion object {
        private const val DATA = "data"
    }

    @Test
    fun `invoking an initiated flow passes the arguments correctly`() {
        val session = mock<FlowSession>()
        val initiatedFlow = InitiatedFlow(TestFlow(), session)
        initiatedFlow.invoke()
        verify(session).send(DATA)
    }

    private class TestFlow : ResponderFlow {
        override fun call(session: FlowSession) {
            session.send(DATA)
        }
    }
}