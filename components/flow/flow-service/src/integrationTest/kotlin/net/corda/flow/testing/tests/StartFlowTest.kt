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
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class StartFlowTest : FlowServiceTestBase() {

    @Test
    fun `Flow starts and runs to completion`() {
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
                    flowTerminatedReason = "flowStartOperationalStatus is INACTIVE, new flows cannot be started for " +
                        "virtual node with shortHash ${CHARLIE_HOLDING_IDENTITY.toCorda().shortHash}"
                )
            }
        }
    }

    /**
     * When a flow event fails with an internal transient exception, the flow engine will automatically retry the failed
     * operation without publishing any extra messages to Kafka. Instead, it will try to re-process the event at a later
     * time using exponential backoff. This could then fail again, triggering the same loop until a threshold is reached.
     *
     * Scenario 1 - Fails multiple times, transient error is fixed before retry limit => flow completes.
     * Scenario 2 - Fails multiple times, transient error persists, hits the retry limit => flow fails and DLQ event.
     */
    @Test
    fun `Retry scenario 1 - Transient exception is fixed within the retry limit and flow succeeds`() {
        given {
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(BOB_HOLDING_IDENTITY)
        }

        val latch = CountDownLatch(1)
        val startFlow = thread {
            latch.countDown()
            `when` {
                startFlowEventReceived(FLOW_ID1, REQUEST_ID1, BOB_HOLDING_IDENTITY, CPI1, "flow start data")
                    .suspendsWith(FlowIORequest.InitialCheckpoint)
                    .completedSuccessfullyWith("hello")
            }
        }

        latch.await()
        Thread.sleep(1000)
        testContext.virtualNode(CPI1, BOB_HOLDING_IDENTITY)
        startFlow.join()

        then {
            expectOutputForFlow(FLOW_ID1) {
                nullStateRecord()
                flowStatus(FlowStates.COMPLETED, result = "hello")
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
            }
        }
    }

    @Test
    fun `Retry scenario 2 - Transient exception is not fixed within the retry limit and flow permanently fails`() {
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
                nullStateRecord()
                markedForDlq()
                noFlowEvents()
                flowFiberCacheDoesNotContainKey(BOB_HOLDING_IDENTITY, REQUEST_ID1)
                // we can't return a status record after the change to checkpoint initialization
                // Story to deal with change in status records -> CORE-10571: Re-design how status record is published
                /*
                flowStatus(
                    state = FlowStates.FAILED,
                    errorType = FlowProcessingExceptionTypes.FLOW_FAILED,
                    errorMessage = "Execution failed with \"Failed to find the virtual node info for holder " +
                        "'HoldingIdentity(x500Name=${BOB_HOLDING_IDENTITY.x500Name}, groupId=${BOB_HOLDING_IDENTITY.groupId})'\" " +
                        "after 1 retry attempts."
                )
                */
            }
        }
    }
}
