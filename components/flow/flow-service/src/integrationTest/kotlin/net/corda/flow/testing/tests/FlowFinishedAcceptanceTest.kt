package net.corda.flow.testing.tests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStates
import net.corda.flow.testing.context.FlowServiceTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class FlowFinishedAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        const val DONE = "Job's done"
    }

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)
            membershipGroupFor(BOB_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }
    }

    @Test
    fun `A flow finishing removes the flow's checkpoint publishes a completed flow status and schedules flow cleanup`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .completedSuccessfullyWith(DONE)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = DONE)
                scheduleFlowMapperCleanupEvents(FlowKey(REQUEST_ID1, ALICE_HOLDING_IDENTITY).toString())
            }
        }
    }

    @Test
    fun `A flow finishing when previously in a retry state publishes a completed flow status and schedules flow cleanup`() {
        // Trigger a retry state
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, CHARLIE_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                checkpointHasRetry(1)
            }
        }

        // Now add the missing vnode and trigger a flow completion
        given {
            virtualNode(CPI1, CHARLIE_HOLDING_IDENTITY)
            membershipGroupFor(CHARLIE_HOLDING_IDENTITY)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, CHARLIE_HOLDING_IDENTITY, CPI1, "flow start data")
                .completedSuccessfullyWith(DONE)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = DONE)
                scheduleFlowMapperCleanupEvents(FlowKey(REQUEST_ID1, CHARLIE_HOLDING_IDENTITY).toString())
            }
        }
    }

    @Test
    fun `A flow finishing with FlowFinished removes fiber from fiber cache`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
                .completedSuccessfullyWith(DONE)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = DONE)
                scheduleFlowMapperCleanupEvents(FlowKey(REQUEST_ID1, BOB_HOLDING_IDENTITY).toString())
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }

    @Test
    fun `An initiated flow finishing removes the flow's checkpoint publishes a completed flow status`() {
        `when` {
            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL)
                .completedSuccessfullyWith(DONE)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = DONE)
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, INITIATED_SESSION_ID_1)
            }
        }
    }
}