package net.corda.flow.testing.tests

import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.virtualnode.OperationalStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class FlowKilledAcceptanceTest : FlowServiceTestBase() {

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            virtualNode(CPI1, BOB_HOLDING_IDENTITY, OperationalStatus.INACTIVE)
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
    fun `test flow start event killed due to inactive flow operational status`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowKilledStatus(flowTerminatedReason = "Flow operational status is INACTIVE")
            }
        }
    }

    @Test
    fun `test init flow event killed due to inactive flow operational status`() {
        `when` {
            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(INITIATED_SESSION_ID_1)
                nullStateRecord()
                flowKilledStatus(flowTerminatedReason = "Flow operational status is INACTIVE")
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
            }
        }
    }
}