package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.schema.configuration.FlowConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class StartFlowTest : FlowServiceTestBase() {

    @Test
    fun `RPC Start Flow - Flow starts and updates stats to running`() {

        given {
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitialCheckpoint)

            wakeupEventReceived(FLOW_ID1)
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                wakeUpEvent()
                flowStatus(FlowStates.RUNNING)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowStatus(FlowStates.COMPLETED, result = "hello")
                nullStateRecord()
            }
        }
    }

    @Test
    fun `RPC Start Flow - missing virtual node causes flow start to retry until success`() {

        // No Virtual Node has been setup
        given {
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            flowConfiguration(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, 1)
        }

        // A Start Flow request is received
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        // We expect the flow to move to a retrying state
        then {
            expectOutputForFlow(FLOW_ID1) {
                noFlowEvents()
                checkpointHasRetry(1)
                flowStatus(FlowStates.RETRYING)
            }
        }

        // Now the virtual node is available
        given {
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
        }

        `when` {
            replayEventFromRetry()
                .suspendsWith(FlowIORequest.InitialCheckpoint)
        }

        // Now we expect the flow to start as normal and clear any retry
        then {
            expectOutputForFlow(FLOW_ID1) {
                checkpointDoesNotHaveRetry()
                wakeUpEvent()
                flowStatus(FlowStates.RUNNING)
            }
        }
    }
}




