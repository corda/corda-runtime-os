package net.corda.flow.testing.tests

import net.corda.data.flow.output.FlowStates
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.schema.configuration.FlowConfig
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.toCorda
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

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.InitialCheckpoint)
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowStatus(FlowStates.COMPLETED, result = "hello")
                nullStateRecord()
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }


    /**
     * When a virtual node has an INACTIVE StartFlowOperationalStatus, it should throw a FlowMarkedForKillException and
     * have a Killed status.
     */
    @Test
    fun `Flow is marked as killed if startFlowOperationalStatus of vNode is INACTIVE`() {

        given {
            virtualNode(CPI1, CHARLIE_HOLDING_IDENTITY, flowStartOperationalStatus = OperationalStatus.INACTIVE)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(CHARLIE_HOLDING_IDENTITY)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, CHARLIE_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                noFlowEvents()
                flowStatus(
                    state = FlowStates.KILLED,
                    flowTerminatedReason = "flowStartOperationalStatus is INACTIVE, new flows cannot be started for virtual node with shortHash ${CHARLIE_HOLDING_IDENTITY.toCorda().shortHash}"
                )
            }
        }
    }

    /**
     * When a flow event fails with a transient exception then the flow will be put into a retry
     * state. In this case the flow engine will publish the problematic event back to itself to be processed at some
     * later time. This could then fail again, triggering the same loop.
     *
     * Scenario 1 - Fails multiple times but completes before the retry limit
     * Scenario 2 - Fails multiple times and hits the retry limit failing the flow to the DLQ
     */

    @Test
    fun `RPC Start Flow - Retry scenario 1 - Fail then succeeds`() {
        given {
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                checkpointHasRetry(1)
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }

        // Now fix the issue and expect the wake-up event to
        // retry the start flow successfully
        given {
            virtualNode(CPI1, BOB_HOLDING_IDENTITY)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
                .completedSuccessfullyWith("hello")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                checkpointDoesNotHaveRetry()
                flowStatus(FlowStates.COMPLETED, result = "hello")
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }

    @Test
    fun `RPC Start Flow - Retry scenario 2 - Hit the retry limit and fail the flow`() {
        given {
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
            flowConfiguration(FlowConfig.PROCESSING_MAX_RETRY_WINDOW_DURATION, 0)
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                checkpointHasRetry(1)
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }

        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
        }
        
        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                markedForDlq()
                noFlowEvents()
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
                //we can't return a status record after the change to checkpoint initialization
                // Story to deal with change in status records -> CORE-10571: Re-design how status record is published
/*                flowStatus(
                    state = FlowStates.FAILED,
                    errorType = FlowProcessingExceptionTypes.FLOW_FAILED,
                    errorMessage = "Execution failed with \"Failed to find the virtual node info for holder " +
                            "'HoldingIdentity(x500Name=${BOB_HOLDING_IDENTITY.x500Name}, groupId=${BOB_HOLDING_IDENTITY.groupId})'\" " +
                            "after 1 retry attempts."
                )*/
            }
        }
    }
}
