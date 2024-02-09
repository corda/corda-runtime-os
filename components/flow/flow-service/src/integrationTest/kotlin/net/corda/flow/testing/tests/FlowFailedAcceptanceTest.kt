package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.testing.context.ALICE_FLOW_KEY_MAPPER
import net.corda.flow.testing.context.FlowServiceTestBase
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
                .completedWithError(EXCEPTION)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                notNullStateRecord()
                flowStatus(FlowStates.FAILED, errorType = FLOW_FAILED, errorMessage = EXCEPTION.message)
                scheduleFlowMapperCleanupEvents(ALICE_FLOW_KEY_MAPPER)
                flowFiberCacheDoesNotContainKey(ALICE_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }

    @Test
    fun `An initiated flow failing removes the flow's checkpoint publishes a failed flow status and schedules flow cleanup`() {
        `when` {
            sessionCounterpartyInfoRequestReceived(FLOW_ID1, INITIATED_SESSION_ID_1, CPI1, PROTOCOL)
                .completedWithError(EXCEPTION)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                notNullStateRecord()
                flowStatus(FlowStates.FAILED, errorType = FLOW_FAILED, errorMessage = EXCEPTION.message)
                scheduleFlowMapperCleanupEvents(INITIATED_SESSION_ID_1)
                flowFiberCacheDoesNotContainKey(ALICE_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }
}