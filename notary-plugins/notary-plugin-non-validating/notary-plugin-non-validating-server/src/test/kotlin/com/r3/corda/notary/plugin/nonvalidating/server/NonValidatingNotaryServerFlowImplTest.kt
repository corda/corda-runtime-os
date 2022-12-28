package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarisationRequestSignature
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneral
import com.r3.corda.notary.plugin.common.NotaryErrorReferenceStateUnknown
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
import net.corda.crypto.testkit.SecureHashUtils
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
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckResponse
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
        val notaryServerParty = Party(MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"), mock())
        val notaryServerIdentity = mock<MemberInfo> {
            on { name } doReturn notaryServerParty.name
            on { sessionInitiationKey } doReturn notaryServerParty.owningKey
        }

        const val DUMMY_PLATFORM_VERSION = 9001

        val aliceKey = mock<PublicKey>()

        val aliceName = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")

        val aliceMemberInfo = mock<MemberInfo> {
            on { platformVersion } doReturn DUMMY_PLATFORM_VERSION
            on { sessionInitiationKey } doReturn aliceKey
            on { name } doReturn aliceName
        }

        /* Uniqueness Client Service */
        val uniquenessCheckResponseSignature = DigitalSignatureAndMetadata(
            mock(),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )

        /* Services */
        val mockMemberLookupService = mock<MemberLookup> {
            on { lookup(eq(aliceName)) } doReturn aliceMemberInfo
            on { myInfo() } doReturn notaryServerIdentity
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
                .contains("Error while processing request from client")
            assertThat(responseError.cause).hasStackTraceContaining(
                "Sig error"
            )
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
                .contains("Error while processing request from client")
            assertThat(responseError.cause).hasStackTraceContaining(
                "Uniqueness checker cannot be reached"
            )
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
    fun `Non-validating notary plugin server should respond with error if time window not present on filtered tx`() {
        createAndCallServer(mockSuccessfulUniquenessClientService(), filteredTxContents = mapOf("timeWindow" to null)) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText).contains(
                "Error while processing request from client"
            )
            assertThat((responseError).cause).hasStackTraceContaining(
                "Time window component could not be found on the transaction"
            )
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if notary not present on filtered tx`() {
        createAndCallServer(mockSuccessfulUniquenessClientService(), filteredTxContents = mapOf("notary" to null)) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText).contains(
                "Error while processing request from client"
            )
            assertThat((responseError).cause).hasStackTraceContaining(
                "Notary component could not be found on the transaction"
            )
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if input states are not audit type in the filtered tx`() {
        val mockInputStateProof = mock<UtxoFilteredData.SizeOnly<StateRef>>()

        createAndCallServer(mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("inputStateRefs" to mockInputStateProof)
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText).contains(
                "Error while processing request from client"
            )
            assertThat((responseError).cause).hasStackTraceContaining(
                "Could not fetch input states from the filtered transaction"
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
                .contains("Error while processing request from client")
            assertThat((responseError).cause).hasStackTraceContaining("DUMMY ERROR")
        }
    }

    @Test
    @Disabled
    // TODO CORE-8976 For now we don't have this check in the server validation code, once we have that, we can enable
    fun `Non-validating notary plugin server should respond with error if notary identity invalid`() {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            // What party we pass in here does not matter, it just needs to be different from the notary server party
            filteredTxContents = mapOf("notary" to Party(MemberX500Name.parse("C=GB,L=London,O=Bob"), mock()))
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText)
                .contains("Error while processing request from client")
            assertThat((responseError).cause).hasStackTraceContaining(
                "Notary server identity does not match with the one attached to the transaction"
            )
        }
    }

    @Suppress("LongParameterList")
    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        filteredTxContents: Map<String, Any?> = emptyMap(),
        sigVerifier: DigitalSignatureVerificationService = mockSigVerifier,
        flowSession: FlowSession? = null,
        txVerificationLogic: () -> Unit = {},
        extractData: (sigs: List<NotarisationResponse>) -> Unit
    ) {

        val txId = SecureHashUtils.randomSecureHash()

        val mockStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateRef>> {
            on { values } doReturn mapOf(0 to StateRef(txId, 0))
        }

        val mockStateAndRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateRef>> {
            on { values } doReturn emptyMap()
        }

        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.SizeOnly<StateAndRef<*>>> {
            on { size } doReturn 1
        }

        val mockTimeWindow = mock<TimeWindow> {
            on { from } doReturn Instant.now()
            on { until } doReturn Instant.now().plusMillis(100000)
        }

        val mockSerializationService = mock<SerializationService> {
            on { serialize(any()) } doReturn SerializedBytes("ABC".toByteArray())
        }

        val filteredTx = mock<UtxoFilteredTransaction> {
            on { notary } doAnswer {
                if (filteredTxContents.containsKey("notary")) {
                    filteredTxContents["notary"] as Party?
                } else {
                    notaryServerParty
                }
            }

            on { timeWindow } doAnswer  {
                if (filteredTxContents.containsKey("timeWindow")) {
                    filteredTxContents["timeWindow"] as TimeWindow?
                } else {
                    mockTimeWindow
                }
            }

            on { inputStateRefs } doAnswer {
                @Suppress("unchecked_cast")
                filteredTxContents["inputStateRefs"] as? UtxoFilteredData<StateRef>
                    ?: mockStateRefUtxoFilteredData
            }

            on { referenceStateRefs } doAnswer {
                @Suppress("unchecked_cast")
                filteredTxContents["referenceStateRefs"] as? UtxoFilteredData<StateRef>
                    ?: mockStateAndRefUtxoFilteredData
            }
            on { outputStateAndRefs } doAnswer {
                @Suppress("unchecked_cast")
                filteredTxContents["outputStateRefs"] as? UtxoFilteredData<StateAndRef<*>>
                    ?: mockOutputStateRefUtxoFilteredData
            }
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
            mockSerializationService,
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

    private fun mockUniquenessClientService(response: LedgerUniquenessCheckResponse) = mock<LedgerUniquenessCheckerClientService> {
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any() )} doReturn response
    }
}
