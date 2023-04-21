package net.corda.flow.testing.tests

import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class WakeupAcceptanceTest : FlowServiceTestBase() {

    @Test
    fun `Receiving a wakeup event for a flow that does not exist discards the event`() {
        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noFlowEvents()
            }
        }
    }

    @Test
    fun `Receiving a wakeup event for a flow that finished discards the event`() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.FlowFinished("done"))
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noFlowEvents()
            }
        }
    }

    @Test
    fun `Receiving a wakeup event for a flow that failed discards the event`() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.FlowFailed(RuntimeException("done")))
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noFlowEvents()
            }
        }
    }
}