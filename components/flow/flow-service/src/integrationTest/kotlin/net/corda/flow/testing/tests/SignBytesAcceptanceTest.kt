package net.corda.flow.testing.tests

import net.corda.data.ExceptionEnvelope
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.testing.context.FlowServiceTestBase
import net.corda.flow.testing.context.flowResumedWithError
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
class SignBytesAcceptanceTest : FlowServiceTestBase() {

    private companion object {
        val SIGNATURE_SPEC = SignatureSpec("SHA256withECDSA")
    }

    @InjectService(timeout = 4000)
    lateinit var cipherSchemaMetadata: CipherSchemeMetadata

    private lateinit var publicKey: PublicKey

    @BeforeEach
    fun beforeEach() {

        publicKey = generateKeyPair().public

        given {
            virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
            cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
            sandboxCpk(CPK1_CHECKSUM)
            membershipGroupFor(ALICE_HOLDING_IDENTITY)

            sessionInitiatingIdentity(ALICE_HOLDING_IDENTITY)
            sessionInitiatedIdentity(BOB_HOLDING_IDENTITY)

            initiatingToInitiatedFlow(PROTOCOL, FAKE_FLOW_NAME, FAKE_FLOW_NAME)
        }
    }

    @Test
    fun `Requesting a signature sends a signing event and resumes after receiving the response`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.SignBytes(REQUEST_ID1, bytes = DATA_MESSAGE_1, publicKey, SIGNATURE_SPEC))

            cryptoSignResponseReceived(FLOW_ID1, REQUEST_ID1, publicKey, DATA_MESSAGE_2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(DigitalSignature.WithKey(publicKey, DATA_MESSAGE_2, emptyMap()))
                cryptoSignEvents()
            }
        }
    }

    @Test
    fun `Receiving a user error response resumes the flow with an error`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.SignBytes(REQUEST_ID1, bytes = DATA_MESSAGE_1, publicKey, SIGNATURE_SPEC))

            cryptoSignResponseReceived(
                FLOW_ID1,
                REQUEST_ID1,
                publicKey,
                DATA_MESSAGE_2,
                exceptionEnvelope = ExceptionEnvelope("USER_ERROR", "Failure")
            )
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                cryptoSignEvents()
            }
        }
    }

    @Test
    fun `Receiving a retriable error response when the retry count is below the threshold resends the signing request and does not resume the flow`() {
        given {
            flowConfiguration(FlowConfig.CRYPTO_MESSAGE_RESEND_WINDOW, -50000L)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.SignBytes(REQUEST_ID1, bytes = DATA_MESSAGE_1, publicKey, SIGNATURE_SPEC))
        }

        `when` {
            cryptoSignResponseReceived(
                FLOW_ID1,
                REQUEST_ID1,
                publicKey,
                DATA_MESSAGE_2,
                exceptionEnvelope = ExceptionEnvelope("RETRY", "Failure")
            )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                cryptoSignEvents(REQUEST_ID1)
            }
        }
    }

    @Test
    fun `Receiving a retriable error response when the retry count is above the threshold resumes the flow with an error`() {
        given {
            flowConfiguration(FlowConfig.CRYPTO_MESSAGE_RESEND_WINDOW, -50000L)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.SignBytes(REQUEST_ID1, bytes = DATA_MESSAGE_1, publicKey, SIGNATURE_SPEC))
        }

        `when` {
            cryptoSignResponseReceived(
                FLOW_ID1,
                REQUEST_ID1,
                publicKey,
                DATA_MESSAGE_2,
                exceptionEnvelope = ExceptionEnvelope("RETRY", "Failure")
            )

            cryptoSignResponseReceived(
                FLOW_ID1,
                REQUEST_ID1,
                publicKey,
                DATA_MESSAGE_2,
                exceptionEnvelope = ExceptionEnvelope("RETRY", "Failure")
            )

            cryptoSignResponseReceived(
                FLOW_ID1,
                REQUEST_ID1,
                publicKey,
                DATA_MESSAGE_2,
                exceptionEnvelope = ExceptionEnvelope("RETRY", "Failure")
            )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWithError<CordaRuntimeException>()
                cryptoSignEvents()
            }
        }
    }

    @Test
    fun `Receive a retriable error response, retry the request, receive wakeup events, successful response received, flow continues`() {
        given {
            flowConfiguration(FlowConfig.CRYPTO_MESSAGE_RESEND_WINDOW, -50000L)

            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.SignBytes(REQUEST_ID1, bytes = DATA_MESSAGE_1, publicKey, SIGNATURE_SPEC))
        }

        `when` {
            cryptoSignResponseReceived(
                FLOW_ID1,
                REQUEST_ID1,
                publicKey,
                DATA_MESSAGE_2,
                exceptionEnvelope = ExceptionEnvelope("RETRY", "Failure")
            )

            wakeupEventReceived(FLOW_ID1)
            wakeupEventReceived(FLOW_ID1)
            wakeupEventReceived(FLOW_ID1)

            cryptoSignResponseReceived(FLOW_ID1, REQUEST_ID1, publicKey, DATA_MESSAGE_2)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowResumedWith(DigitalSignature.WithKey(publicKey, DATA_MESSAGE_2, emptyMap()))
                cryptoSignEvents()
            }
        }
    }

    @Test
    fun `Receiving a platform error response resumes the flow with an error`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.SignBytes(REQUEST_ID1, bytes = DATA_MESSAGE_1, publicKey, SIGNATURE_SPEC))

            cryptoSignResponseReceived(
                FLOW_ID1,
                REQUEST_ID1,
                publicKey,
                DATA_MESSAGE_2,
                exceptionEnvelope = ExceptionEnvelope("PLATFORM_ERROR", "Failure")
            )
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
                markedForDlq()
            }
        }
    }

    @Test
    fun `Receiving a non-crypto event does not resume the flow`() {
        `when` {
            startFlowEventReceived(FLOW_ID1, REQUEST_ID1, ALICE_HOLDING_IDENTITY, CPI1, "flow start data")
                .suspendsWith(FlowIORequest.SignBytes(REQUEST_ID1, bytes = DATA_MESSAGE_1, publicKey, SIGNATURE_SPEC))

            wakeupEventReceived(FLOW_ID1)
                .suspendsWith(FlowIORequest.ForceCheckpoint)
        }

        then {
            expectOutputForFlow(FLOW_ID1) {
                cryptoSignEvents(REQUEST_ID1)
            }

            expectOutputForFlow(FLOW_ID1) {
                flowDidNotResume()
            }
        }
    }

    private fun generateKeyPair(): KeyPair {
        val scheme = cipherSchemaMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val keyPairGenerator = KeyPairGenerator.getInstance(
            scheme.algorithmName,
            cipherSchemaMetadata.providers.getValue(scheme.providerName)
        )
        if (scheme.algSpec != null) {
            keyPairGenerator.initialize(scheme.algSpec, cipherSchemaMetadata.secureRandom)
        } else if (scheme.keySize != null) {
            keyPairGenerator.initialize(scheme.keySize!!, cipherSchemaMetadata.secureRandom)
        }
        return keyPairGenerator.generateKeyPair()
    }
}