package net.corda.flow.testing.tests.persistence

import java.nio.ByteBuffer
import net.corda.data.persistence.FindEntity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.CPK1
import net.corda.flow.testing.tests.FLOW_ID1
import net.corda.flow.testing.tests.REQUEST_ID1
import net.corda.schema.configuration.FlowConfig.PERSISTENCE_MESSAGE_RESEND_WINDOW
import net.corda.schema.configuration.FlowConfig.PERSISTENCE_RESEND_BUFFER
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class EntityRequestResendAcceptanceTest : FlowServiceTestBase() {

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
            cpkMetadata(CPI1, CPK1)
            sandboxCpk(CPK1)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)
        }
    }

    @Test
    fun `Given a request has been sent with no response within the resend window, the request is not resent`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.Find(
                        requestId,
                        className,
                        bytes
                    ))
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                noEntityRequestSent()
            }
        }
    }

    @Test
    fun `Given a request has been sent and the resend window has been surpased, the request is resent`() {
        given {
            flowConfiguration(PERSISTENCE_RESEND_BUFFER, 0L)
            flowConfiguration(PERSISTENCE_MESSAGE_RESEND_WINDOW, 0L)
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.Find(
                        requestId,
                        className,
                        bytes
                    ))
        }

        `when` {
            wakeupEventReceived(FLOW_ID1)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                entityRequestSent(FindEntity(className, byteBuffer))
                flowDidNotResume()
            }
        }
    }
}
