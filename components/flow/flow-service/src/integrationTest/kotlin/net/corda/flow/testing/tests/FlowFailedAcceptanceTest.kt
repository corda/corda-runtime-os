package net.corda.flow.testing.tests

import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStates
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.initiateFlowMessageFor
import net.corda.flow.utils.emptyKeyValuePairList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class FlowFailedAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        val EXCEPTION = RuntimeException("Job's done")
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
    fun `A flow failing removes the flow's checkpoint publishes a failed flow status and schedules flow cleanup`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.FlowFailed(EXCEPTION))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.FAILED, errorType = FLOW_FAILED, errorMessage = EXCEPTION.message)
                scheduleFlowMapperCleanupEvents(FlowKey(REQUEST_ID1, ALICE_HOLDING_IDENTITY).toString())
            }
        }
    }

    @Test
    fun `An initiated flow failing removes the flow's checkpoint publishes a failed flow status and schedules flow cleanup`() {
        `when` {
            sessionInitEventReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL)
                .suspendsWith(FlowIORequest.FlowFailed(EXCEPTION))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.FAILED, errorType = FLOW_FAILED, errorMessage = EXCEPTION.message)
                scheduleFlowMapperCleanupEvents(FlowKey(INITIATED_SESSION_ID_1, BOB_HOLDING_IDENTITY).toString())
            }
        }
    }

    @Test
    fun `Given the flow has a WAIT_FOR_FINAL_ACK session receiving a session close event and then failing the flow schedules flow and session cleanup`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    initiateFlowMessageFor(initiatedIdentityMemberName, SESSION_ID_1)
                )

            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)

            sessionCloseEventReceived(FLOW_ID1, SESSION_ID_1, sequenceNum = 1, receivedSequenceNum = 1)
                .suspendsWith(FlowIORequest.CloseSessions(setOf(SESSION_ID_1)))
        }

        `when` {
            sessionAckEventReceived(FLOW_ID1, SESSION_ID_1, receivedSequenceNum = 2)
                .suspendsWith(FlowIORequest.FlowFailed(EXCEPTION))
        }



        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.FAILED, errorType = FLOW_FAILED, errorMessage = EXCEPTION.message)
                scheduleFlowMapperCleanupEvents(FlowKey(REQUEST_ID1, ALICE_HOLDING_IDENTITY).toString(), SESSION_ID_1)
            }
        }
    }
}