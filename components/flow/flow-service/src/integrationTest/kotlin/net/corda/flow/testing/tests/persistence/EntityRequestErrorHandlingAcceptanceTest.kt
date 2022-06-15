package net.corda.flow.testing.tests.persistence

import java.nio.ByteBuffer
import net.corda.data.ExceptionEnvelope
import net.corda.data.persistence.Error
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.CPK1
import net.corda.flow.testing.tests.FLOW_ID1
import net.corda.flow.testing.tests.REQUEST_ID1
import net.corda.v5.persistence.CordaPersistenceException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class EntityRequestErrorHandlingAcceptanceTest : FlowServiceTestBase() {

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
    fun `Given an entity request has been sent, if a response is received that does not match the request, ignore it`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.Find(
                        requestId,
                        className,
                        bytes
                    )
                )
        }

        `when` {
            entityResponseSuccessReceived(FLOW_ID1, "invalidId", byteBuffer)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
        }
    }

    @Test
    fun `Receive a 'retriable' error response, retry the request, successful response received, flow continues`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.Find(
                        requestId,
                        className,
                        bytes
                    )
                )
        }

        `when` {
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
            entityResponseSuccessReceived(FLOW_ID1, requestId, byteBuffer)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(byteBuffer)
            }
        }
    }
    @Test
    fun `Receive a 'retriable' error response, retry the request max times, error response received always, flow errors`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.Find(
                        requestId,
                        className,
                        bytes
                    )
                )
        }

        `when` {
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.VIRTUAL_NODE, ExceptionEnvelope("", ""))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError(CordaPersistenceException::class.java)
            }
        }
    }

    @Test
    fun `Receive a 'not ready' response, retry the request multiple times, success received eventually, flow continues`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.Find(
                        requestId,
                        className,
                        bytes
                    )
                )
        }

        `when` {
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.NOT_READY, ExceptionEnvelope("", ""))
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.NOT_READY, ExceptionEnvelope("", ""))
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.NOT_READY, ExceptionEnvelope("", ""))
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.NOT_READY, ExceptionEnvelope("", ""))
            entityResponseSuccessReceived(FLOW_ID1, requestId, byteBuffer)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(byteBuffer)
            }
        }
    }

    @Test
    fun `Receive a 'fatal' response, does not retry the request, flow errors`() {
        given {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(
                    FlowIORequest.Find(
                        requestId,
                        className,
                        bytes
                    )
                )
        }

        `when` {
            entityResponseErrorReceived(FLOW_ID1, requestId, Error.FATAL, ExceptionEnvelope("", ""))
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError(CordaPersistenceException::class.java)
                noEntityRequestSent()
            }
        }
    }
}
