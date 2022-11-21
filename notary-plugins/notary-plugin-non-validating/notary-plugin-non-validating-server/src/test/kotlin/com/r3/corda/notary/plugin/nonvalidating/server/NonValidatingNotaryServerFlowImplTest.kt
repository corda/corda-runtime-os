package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarisationRequestSignature
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneral
import com.r3.corda.notary.plugin.common.NotaryErrorReferenceStateUnknown
import com.r3.corda.notary.plugin.nonvalidating.api.INPUTS_GROUP
import com.r3.corda.notary.plugin.nonvalidating.api.NOTARY_GROUP
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
import com.r3.corda.notary.plugin.nonvalidating.api.OUTPUTS_GROUP
import com.r3.corda.notary.plugin.nonvalidating.api.REFERENCES_GROUP
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResponseImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.uniqueness.model.UniquenessCheckResponse
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonValidatingNotaryServerFlowImplTest {

    private companion object {

        /* Cache for storing response from server */
        val responseFromServer = mutableListOf<NotarisationResponse>()

        /* Members */
        const val DUMMY_PLATFORM_VERSION = 9001

        val aliceKey = mock<PublicKey>()

        val aliceName = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")

        val aliceMemberInfo = mock<MemberInfo> {
            on { platformVersion } doReturn DUMMY_PLATFORM_VERSION
            on { sessionInitiationKey } doReturn aliceKey
            on { name } doReturn aliceName
        }

        /* Component group hashes */
        val twHash = "Time".toByteArray()
        val inputHash = "Input".toByteArray()
        val inputHash2 = "Input2".toByteArray()
        val refHash = "Ref".toByteArray()
        val outputHash = "Output".toByteArray()

        /* Uniqueness Client Service */
        val uniquenessCheckResponseSignature = DigitalSignatureAndMetadata(
            mock(),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )

        /* Services */
        val mockMemberLookupService = mock<MemberLookup> {
            on { lookup(eq(aliceName)) } doReturn aliceMemberInfo
        }

        val mockSigVerifier = mock<DigitalSignatureVerificationService> {
            // Do nothing
            on { verify(any(), any(), any(), any()) } doAnswer { }
        }
    }

    @BeforeEach
    fun clearCache() {
        responseFromServer.clear()
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if request signature is invalid`() {
        val mockSigVerifierError = mock<DigitalSignatureVerificationService> {
            on { verify(any(), any(), any(), any()) } doThrow IllegalArgumentException("Sig error")
        }

        createAndCallServer(mockSuccessfulUniquenessClientService(), sigVerifier = mockSigVerifierError) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText)
                .contains("Error while verifying request signature. Cause: java.lang.IllegalArgumentException: Sig error")
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if the uniqueness check fails`() {
        createAndCallServer(mockErrorUniquenessClientService()) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryErrorReferenceStateUnknown::class.java)
            assertThat((responseError as NotaryErrorReferenceStateUnknown).unknownStates).isEmpty()
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if an error encountered during uniqueness check`() {
        createAndCallServer(mockThrowErrorUniquenessCheckClientService()) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText)
                .contains("Uniqueness checker cannot be reached")
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with signatures if the uniqueness check successful`() {
        createAndCallServer(mockSuccessfulUniquenessClientService()) {
            assertThat(responseFromServer).hasSize(1)

            val response = responseFromServer.first()
            assertThat(response.error).isNull()
            assertThat(response.signatures).hasSize(1)
            assertThat(response.signatures.first()).isEqualTo(uniquenessCheckResponseSignature)
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if time window cannot be deserialised`() {
        val errorSerializationService = mock<SerializationService> {
            on { serialize(any()) } doReturn SerializedBytes("ABC".toByteArray())
            on { deserialize(twHash, TimeWindow::class.java) } doThrow
                    IllegalArgumentException("Cannot deserialize time window")
        }
        createAndCallServer(mockSuccessfulUniquenessClientService(), errorSerializationService) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText).contains(
                "Could not validate request. Reason: Cannot deserialize time window"
            )
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if transaction verification fails`() {
        fun throwVerify() {
            throw IllegalArgumentException("DUMMY ERROR")
        }
        createAndCallServer(mockSuccessfulUniquenessClientService(), txVerificationLogic = ::throwVerify) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText)
                .contains("Error while validating the transaction, reason: DUMMY ERROR")
        }
    }

    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        serializationService: SerializationService? = null,
        sigVerifier: DigitalSignatureVerificationService = mockSigVerifier,
        flowSession: FlowSession? = null,
        txVerificationLogic: () -> Unit = {},
        extractData: (sigs: List<NotarisationResponse>) -> Unit
    ) {

        val txId = SecureHashUtils.randomSecureHash()

        val mockStateRef = mock<StateAndRef<*>> {
            on { ref } doReturn StateRef(txId, 0)
        }

        val mockTimeWindow = mock<TimeWindow> {
            on { from } doReturn Instant.now()
            on { until } doReturn Instant.now().plusMillis(100000)
        }

        val paramOrDefaultSerializationService = serializationService
            ?: mock {
                on { serialize(any()) } doReturn SerializedBytes("ABC".toByteArray())
                on { deserialize(twHash, TimeWindow::class.java) } doReturn mockTimeWindow
                on { deserialize(inputHash, StateAndRef::class.java) } doReturn mockStateRef
                on { deserialize(inputHash2, StateAndRef::class.java) } doReturn mockStateRef
                on { deserialize(refHash, StateAndRef::class.java) } doReturn mockStateRef
                on { deserialize(outputHash, TransactionState::class.java) } doReturn mock()
            }

        val filteredTx = mock<FilteredTransaction> {
            on { getComponentGroupContent(NOTARY_GROUP) } doReturn listOf(
                Pair(0, "NOTARY".toByteArray()),
                Pair(1, twHash)
            )
            on { getComponentGroupContent(INPUTS_GROUP) } doReturn listOf(Pair(0, inputHash), Pair(1, inputHash2))
            on { getComponentGroupContent(OUTPUTS_GROUP) } doReturn listOf(Pair(0, outputHash))
            on { getComponentGroupContent(REFERENCES_GROUP) } doReturn listOf(Pair(0, refHash))
            on { verify() } doAnswer {
                txVerificationLogic()
            }
            on { id } doReturn txId
        }

        val paramOrDefaultSession = flowSession ?: mock {
            on { receive(NonValidatingNotarisationPayload::class.java) } doReturn NonValidatingNotarisationPayload(
                filteredTx,
                NotarisationRequestSignature(
                    DigitalSignature.WithKey(
                        aliceKey,
                        "ABC".toByteArray(),
                        emptyMap()
                    ),
                    DUMMY_PLATFORM_VERSION
                )
            )
            on { send(any()) } doAnswer {
                responseFromServer.add(it.arguments.first() as NotarisationResponse)
                Unit
            }
            on { counterparty } doReturn aliceName
        }

        val server = NonValidatingNotaryServerFlowImpl(
            clientService,
            paramOrDefaultSerializationService,
            sigVerifier,
            mockMemberLookupService
        )

        server.call(paramOrDefaultSession)

        extractData(responseFromServer)
    }

    private fun mockSuccessfulUniquenessClientService(): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(UniquenessCheckResponseImpl(
            UniquenessCheckResultSuccessImpl(Instant.now()),
            uniquenessCheckResponseSignature
        ))
    }

    private fun mockErrorUniquenessClientService(): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(UniquenessCheckResponseImpl(
            UniquenessCheckResultFailureImpl(Instant.now(), UniquenessCheckErrorReferenceStateUnknownImpl(emptyList())),
            null
        ))
    }

    private fun mockThrowErrorUniquenessCheckClientService() = mock<LedgerUniquenessCheckerClientService> {
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any() )} doThrow
                IllegalArgumentException("Uniqueness checker cannot be reached")
    }

    private fun mockUniquenessClientService(response: UniquenessCheckResponse) = mock<LedgerUniquenessCheckerClientService> {
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any() )} doReturn response
    }
}
