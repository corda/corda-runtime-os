package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.ALICE_FLOW_KEY_MAPPER
import net.corda.flow.testing.context.BOB_FLOW_KEY_MAPPER
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
            virtualNode(CPI1, BOB_HOLDING_IDENTITY, flowOperationalStatus = OperationalStatus.INACTIVE)
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
                sessionErrorEvents()
                nullStateRecord()
                flowKilledStatus(flowTerminatedReason = "Flow operational status is INACTIVE")
                scheduleFlowMapperCleanupEvents(BOB_FLOW_KEY_MAPPER)
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }

    @Test
    fun `test init flow event killed due to inactive flow operational status`() {
        `when` {
            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(INITIATED_SESSION_ID_1)
                nullStateRecord()
                flowKilledStatus(flowTerminatedReason = "Flow operational status is INACTIVE")
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }

    @Test
    fun `flow removed from cache when flow resumes for virtual node with flow operational status inactive`() {

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitialCheckpoint)
                .suspendsWith(FlowIORequest.Receive(setOf(
                    SessionInfo(SESSION_ID_1, initiatedIdentityMemberName),
                )))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noOutputEvent()
                flowStatus(FlowStates.RUNNING)
                flowFiberCacheContainsKey(ALICE_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }

        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY, flowOperationalStatus = OperationalStatus.INACTIVE)
        }

        `when` {
            sessionDataEventReceived(FLOW_ID1, SESSION_ID_1, DATA_MESSAGE_1, sequenceNum = 1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                sessionErrorEvents(SESSION_ID_1)
                nullStateRecord()
                flowKilledStatus(flowTerminatedReason = "Flow operational status is INACTIVE")
                scheduleFlowMapperCleanupEvents(SESSION_ID_1, ALICE_FLOW_KEY_MAPPER)
                flowFiberCacheDoesNotContainKey(ALICE_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }
}
