package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.schema.configuration.FlowConfig
import net.corda.virtualnode.OperationalStatus
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
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
        }

        println("printing")

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
    fun `RPC Start Flow - Flow throws FlowMarkedForKillException if startFlowOperationalStatus is INACTIVE`() {

        given {
            virtualNode(CPI1, CHARLIE_HOLDING_IDENTITY, flowStartOperationalStatus = OperationalStatus.INACTIVE)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(CHARLIE_HOLDING_IDENTITY)
        }

        println("printing")

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, CHARLIE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitialCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
//                nullStateRecord()
//                markedForDlq() ?
//                noFlowEvents()
                flowStatus(
                    state = FlowStates.KILLED,
                    errorType = FlowProcessingExceptionTypes.FLOW_FAILED,
                    errorMessage = "'flowStartOperationalStatus is INACTIVE, new flows cannot be started for ${CHARLIE_HOLDING_IDENTITY.x500Name}"
                )
            }
        }
    }

    /**
     * When a flow event fails with a transient exception then the flow will be put into a retry
     * state. In this state the failed event will be retried when the flow receives a wakeup, if the retry
     * is successful then the retry state is cleared and the flow continues as expected, if the retry limit is reached
     * then the flow is failed to the DLQ

     * Scenario 1 - Fails multiple times but completes before the retry limit
     * Scenario 2 - Fails multiple times and hits the retry limit failing the flow to the DLQ
     */

    @Test
    fun `RPC Start Flow - Retry scenario 1 - Fail then succeeds`() {
        given {
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            flowConfiguration(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, 3)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noFlowEvents()
                checkpointHasRetry(1)
                flowStatus(FlowStates.RETRYING)
            }
        }

        // Retry a second time
        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noFlowEvents()
                checkpointHasRetry(2)
                flowStatus(FlowStates.RETRYING)
            }
        }

        // Now fix the issue and expect the wake-up event to
        // retry the start flow successfully
        given {
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.InitialCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                checkpointDoesNotHaveRetry()
                wakeUpEvent()
                flowStatus(FlowStates.RUNNING)
            }
        }
    }

    @Test
    fun `RPC Start Flow - Retry scenario 2 - Hit the retry limit and fail the flow`() {
        given {
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            flowConfiguration(FlowConfig.PROCESSING_MAX_RETRY_ATTEMPTS, 1)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                noFlowEvents()
                checkpointHasRetry(1)
                flowStatus(FlowStates.RETRYING)
            }
        }

        // Retry a second time
        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                markedForDlq()
                noFlowEvents()
                flowStatus(
                    state = FlowStates.FAILED,
                    errorType = FlowProcessingExceptionTypes.FLOW_FAILED,
                    errorMessage = "Execution failed with \"Failed to create the sandbox: Failed to find the virtual node info for holder 'HoldingIdentity(x500Name=${BOB_HOLDING_IDENTITY.x500Name}, groupId=${BOB_HOLDING_IDENTITY.groupId})' in class net.corda.virtualnode.read.fake.VirtualNodeInfoReadServiceFake\" after 1 retry attempts."
                )
            }
        }
    }
}
