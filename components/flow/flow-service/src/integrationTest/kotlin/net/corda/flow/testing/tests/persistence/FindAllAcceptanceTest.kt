package net.corda.flow.testing.tests.persistence

import java.nio.ByteBuffer
import net.corda.data.persistence.FindAll
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.CPK1
import net.corda.flow.testing.tests.CPK1_CHECKSUM
import net.corda.flow.testing.tests.FLOW_ID1
import net.corda.flow.testing.tests.REQUEST_ID1
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class FindAllAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        const val requestId = "requestId"
        const val className = "className"
        val bytes = "bytes".toByteArray()
        val byteBuffer = ByteBuffer.wrap(bytes)
    }

    @BeforeEach
    fun beforeEach() {
        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)
        }
    }

    @Test
    fun `Calling 'findAll' on a flow sends an EntityRequest with payload FindAll`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.FindAll(requestId, className))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                entityRequestSent(FindAll(className, 0, Int.MAX_VALUE))
            }
        }
    }

    @Test
    fun `Receiving a null response from a FindAll request resumes the flow`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.FindAll(requestId, className))
        }

        `when` {
            entityResponseSuccessReceived(FLOW_ID1, requestId, null)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(null)
            }
        }
    }

    @Test
    fun `Receiving bytes response from a FindAll request resumes the flow`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.FindAll(requestId, className))
        }

        `when` {
            entityResponseSuccessReceived(FLOW_ID1, requestId, byteBuffer)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(byteBuffer)
            }
        }
    }
}
