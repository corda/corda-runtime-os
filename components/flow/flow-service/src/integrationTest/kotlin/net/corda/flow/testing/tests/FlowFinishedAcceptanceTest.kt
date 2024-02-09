package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
import net.corda.flow.testing.context.ALICE_FLOW_KEY_MAPPER
import net.corda.flow.testing.context.BOB_FLOW_KEY_MAPPER
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
                notNullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = DONE)
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER)
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
                notNullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = DONE)
                scheduleFlowMapperCleanupEvents(BOB_FLOW_KEY_MAPPER)
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }

    @Test
    fun `An initiated flow finishing removes the flow's checkpoint publishes a completed flow status`() {
        `when` {
            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL)
                .completedSuccessfullyWith(DONE)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                notNullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = DONE)
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, INITIATED_SESSION_ID_1)
            }
        }
    }
}
