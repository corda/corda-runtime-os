package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarisationRequestSignature
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneral
import com.r3.corda.notary.plugin.common.NotaryErrorReferenceStateUnknown
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowBetweenImpl
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
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
        val bobName = MemberX500Name("Bob", "Bob Corp", "LDN", "GB")

        val aliceMemberInfo = mock<MemberInfo> {
            on { platformVersion } doReturn DUMMY_PLATFORM_VERSION
            on { sessionInitiationKey } doReturn aliceKey
            on { name } doReturn aliceName
        }

        val bobMemberInfo = mock<MemberInfo> {
            on { platformVersion } doReturn DUMMY_PLATFORM_VERSION
            on { sessionInitiationKey } doReturn mock()
            on { name } doReturn bobName
        }

        /* Uniqueness Client Service */
        val uniquenessCheckResponseSignature = DigitalSignatureAndMetadata(
            mock(),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )

        /* State Refs */
        val mockStateAndRef = mock<StateAndRef<*>> {
            on { ref } doReturn StateRef(SecureHashUtils.randomSecureHash(), 0)
        }

        /* Transactions */
        val mockLedgerTransaction = mock<UtxoLedgerTransaction> {
            on { inputStateAndRefs } doReturn listOf(mockStateAndRef)
            on { referenceInputStateAndRefs } doReturn emptyList()
            on { timeWindow } doReturn TimeWindowBetweenImpl(Instant.now(), Instant.now().plusMillis(1000000))
        }

        val txId = SecureHashUtils.randomSecureHash()

        val mockUtxoTx = mock<UtxoSignedTransaction> {
            on { toLedgerTransaction() } doReturn mockLedgerTransaction
            on { id } doReturn txId
        }

        /* Session and payload from "client" */
        val mockClientSession = mock<FlowSession> {
            on { receive(NonValidatingNotarisationPayload::class.java) } doReturn NonValidatingNotarisationPayload(
                mockUtxoTx,
                0,
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

        /* Services */
        val mockMemberLookupService = mock<MemberLookup> {
            on { myInfo() } doReturn bobMemberInfo
            on { lookup(eq(aliceName)) } doReturn aliceMemberInfo
            on { lookup(eq(bobName)) } doReturn bobMemberInfo
        }

        val mockSerializationService = mock<SerializationService> {
            on { serialize(any()) } doReturn SerializedBytes("ABC".toByteArray())
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

        createAndCallServer(mockSuccessfulUniquenessClientService(), mockSigVerifierError) {
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
    // TODO CORE-7249 Spying won't be necessary after the actual logic is implemented, after that we can use
    //  `createAndCallServer` here as well
    fun `Non-validating notary plugin server should respond with error if request is invalid`() {
        // Spy server flow so it throws exception on request validation
        // TODO Figure out why kotlin spy doesn't work
        val server = Mockito.spy(NonValidatingNotaryServerFlowImpl())

        // TODO Once we have actual logic, this won't be needed
        Mockito.doThrow(
            IllegalStateException("Request could not be validated")
        ).whenever(server).validateRequest(any(), any())

        server.call(mockClientSession)

        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
        assertThat((responseError as NotaryErrorGeneral).errorText).contains("Request could not be validated")
    }

    @Test
    // TODO CORE-7249 Spying won't be necessary after the actual logic is implemented, after that we can use
    //  `createAndCallServer` here as well
    fun `Non-validating notary plugin server should respond with error if transaction verification fails`() {
        // Spy server flow so it throws exception on request validation
        // TODO Figure out why kotlin spy doesn't work
        val server = Mockito.spy(
            NonValidatingNotaryServerFlowImpl(
                mockSuccessfulUniquenessClientService(),
                mockSerializationService,
                mockSigVerifier,
                mockMemberLookupService
            )
        )

        // TODO Once we have actual logic, this won't be needed
        Mockito.doThrow(
            IllegalStateException("Request could not be verified")
        ).whenever(server).verifyTransaction(any())

        server.call(mockClientSession)

        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
        assertThat((responseError as NotaryErrorGeneral).errorText).contains("Request could not be verified")
    }

    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        sigVerifier: DigitalSignatureVerificationService = mockSigVerifier,
        extractData: (sigs: List<NotarisationResponse>) -> Unit
    ) {
        val server = NonValidatingNotaryServerFlowImpl(
            clientService,
            mockSerializationService,
            sigVerifier,
            mockMemberLookupService
        )

        server.call(mockClientSession)

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
