package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarizationRequestSignature
import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.common.NotaryExceptionReferenceStateUnknown
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarizationPayload
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorUnhandledExceptionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonValidatingNotaryServerFlowImplTest {

    private companion object {
        const val DUMMY_PLATFORM_VERSION = 9001

        /* Cache for storing response from server */
        val responseFromServer = mutableListOf<NotarizationResponse>()

        /* Notary VNodes */
        val notaryVNodeAliceKey = mock<PublicKey>()
        val notaryVNodeBobKey = mock<PublicKey>()

        /* Notary Service */
        val notaryServiceCompositeKey = mock<CompositeKey> {
            on { leafKeys } doReturn setOf(notaryVNodeAliceKey, notaryVNodeBobKey)
        }

        val notaryServiceParty = Party(
            MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"),
            notaryServiceCompositeKey
        )

        /* Member - The client that initiated the session with the notary server */
        val memberCharlieKey = mock<PublicKey>()

        // The client that initiated the session with the notary server
        val memberCharlieParty = Party(
            MemberX500Name.parse("O=MemberCharlie, L=London, C=GB"),
            memberCharlieKey
        )

        val memberCharlieMemberInfo = mock<MemberInfo> {
            on { name } doReturn memberCharlieParty.name
            on { sessionInitiationKey } doReturn memberCharlieParty.owningKey
        }

        // Default signature verifier, no verification
        val mockSigVerifier = mock<DigitalSignatureVerificationService> {
            // Do nothing
            on { verify(any(), any<ByteArray>(), any(), any()) } doAnswer { }
        }

        val mockTransactionSignatureService = mock<TransactionSignatureService>()
    }

    @BeforeEach
    fun clearCache() {
        responseFromServer.clear()
        whenever(mockTransactionSignatureService.signBatch(any(), any())).doAnswer { inv ->
            List((inv.arguments.first() as List<*>).size) {
                @Suppress("unchecked_cast")
                (inv.arguments[1] as List<PublicKey>).map { publicKey ->
                    getSignatureWithMetadataExample(
                        publicKey
                    )
                }
            }
        }
    }

    @Test
    fun `Non-validating notary should respond with error if no keys found for signing`() {
        // We sign with a key that is not part of the notary composite key
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenThrow(
            TransactionNoAvailableKeysException("The publicKeys do not have any private counterparts available.", null)
        )
        createAndCallServer(mockSuccessfulUniquenessClientService()) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Error while processing request from client")
        }
    }

    @Test
    fun `Non-validating notary should respond with success if the notary key is simple`() {
        // We sign with a key that is not part of the notary composite key
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            notaryServiceKey = notaryVNodeAliceKey
        ) {
            assertThat(responseFromServer).hasSize(1)

            val response = responseFromServer.first()
            assertThat(response.error).isNull()
            assertThat(response.signatures).hasSize(1)
            assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey)
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if request signature is invalid`() {
        val mockSigVerifierError = mock<DigitalSignatureVerificationService> {
            on { verify(any(), any<ByteArray>(), any(), any()) } doThrow IllegalArgumentException("Sig error")
        }

        createAndCallServer(mockSuccessfulUniquenessClientService(), sigVerifier = mockSigVerifierError) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Error while processing request from client")
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if the uniqueness check fails`() {
        createAndCallServer(mockErrorUniquenessClientService(UniquenessCheckErrorReferenceStateUnknownImpl(emptyList()))) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryExceptionReferenceStateUnknown::class.java)
            assertThat((responseError as NotaryExceptionReferenceStateUnknown).unknownStates).isEmpty()
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if an error encountered during uniqueness check`() {
        createAndCallServer(mockThrowErrorUniquenessCheckClientService()) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Error while processing request from client")
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with signatures if the uniqueness check successful`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(
                listOf(
                    getSignatureWithMetadataExample(
                        notaryVNodeAliceKey
                    )
                )
            )
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
        ) {
            assertThat(responseFromServer).hasSize(1)

            val response = responseFromServer.first()
            assertThat(response.error).isNull()
            assertThat(response.signatures).hasSize(1)
            assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey)
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if time window not present on filtered tx`() {
        createAndCallServer(mockSuccessfulUniquenessClientService(), filteredTxContents = mapOf("timeWindow" to null)) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText).contains(
                "Error while processing request from client"
            )
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if notary not present on filtered tx`() {
        createAndCallServer(mockSuccessfulUniquenessClientService(), filteredTxContents = mapOf("notary" to null)) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText).contains(
                "Error while processing request from client"
            )
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with error if input states are not audit type in the filtered tx`() {
        val mockInputStateProof = mock<UtxoFilteredData.SizeOnly<StateRef>>()

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("inputStateRefs" to mockInputStateProof)
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText).contains(
                "Error while processing request from client"
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
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Error while processing request from client")
        }
    }

    @Test
    fun `Non-validating notary plugin server should respond with general error if unhandled exception occurred in uniqueness checker`() {
        createAndCallServer(mockErrorUniquenessClientService(
            UniquenessCheckErrorUnhandledExceptionImpl(
                IllegalArgumentException::class.java.name,
                "Unhandled error!"
            )
        )) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Unhandled exception of type java.lang.IllegalArgumentException encountered during " +
                        "uniqueness checking with message: Unhandled error!")
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
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Error while processing request from client")
        }
    }

    @Suppress("LongParameterList")
    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        notaryServiceKey: PublicKey = notaryServiceCompositeKey,
        filteredTxContents: Map<String, Any?> = emptyMap(),
        sigVerifier: DigitalSignatureVerificationService = mockSigVerifier,
        flowSession: FlowSession? = null,
        txVerificationLogic: () -> Unit = {},
        extractData: (sigs: List<NotarizationResponse>) -> Unit
    ) {
        val txId = randomSecureHash()

        // 1. Set up the defaults for the filtered transaction
        val transactionMetadata = mock<TransactionMetadata>()

        val mockTimeWindow = mock<TimeWindow> {
            on { from } doReturn Instant.now()
            on { until } doReturn Instant.now().plusMillis(100000)
        }

        val mockStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateRef>> {
            on { values } doReturn mapOf(0 to StateRef(txId, 0))
        }

        val mockStateAndRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateRef>> {
            on { values } doReturn emptyMap()
        }

        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.SizeOnly<StateAndRef<*>>> {
            on { size } doReturn 1
        }

        // 2. Check if any filtered transaction data should be overwritten
        val filteredTx = mock<UtxoFilteredTransaction> {
            on { notary } doAnswer {
                if (filteredTxContents.containsKey("notary")) {
                    filteredTxContents["notary"] as Party?
                } else {
                    notaryServiceParty
                }
            }

            on { metadata } doAnswer {
                transactionMetadata
            }

            on { timeWindow } doAnswer {
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

        // 3. Mock the receive and send from the counterparty session, unless it is overwritten
        val paramOrDefaultSession = flowSession ?: mock {
            on { receive(NonValidatingNotarizationPayload::class.java) } doReturn NonValidatingNotarizationPayload(
                filteredTx,
                NotarizationRequestSignature(
                    DigitalSignature.WithKey(
                        memberCharlieKey,
                        "ABC".toByteArray()
                    ),
                    DUMMY_PLATFORM_VERSION
                ),
                notaryServiceKey
            )
            on { send(any()) } doAnswer {
                responseFromServer.add(it.arguments.first() as NotarizationResponse)
                Unit
            }
            on { counterparty } doReturn memberCharlieParty.name
        }

        // 4. Mock my member info to always return a platform version, and make sure we get the right party when
        // we lookup the counterparty
        val mockMemberInfo = mock<MemberInfo> {
            on { platformVersion } doReturn DUMMY_PLATFORM_VERSION
        }
        val mockMemberLookup = mock<MemberLookup> {
            on { lookup(memberCharlieParty.name) } doReturn memberCharlieMemberInfo
            on { myInfo() } doReturn mockMemberInfo
        }

        // 5. Serialization service has no part in this testing so just returning a dummy
        val mockSerializationService = mock<SerializationService> {
            on { serialize(any<Any>()) } doReturn SerializedBytes("ABC".toByteArray())
        }

        val mockDigestService = mock<DigestService> {
            on { hash(any<ByteArray>(), any()) } doReturn
                    SecureHashImpl("dummy", byteArrayOf(0x01, 0x02, 0x03, 0x04))
        }

        val server = NonValidatingNotaryServerFlowImpl(
            clientService,
            mockSerializationService,
            sigVerifier,
            mockMemberLookup,
            mockTransactionSignatureService,
            mockDigestService
        )

        server.call(paramOrDefaultSession)

        extractData(responseFromServer)
    }

    private fun mockSuccessfulUniquenessClientService(): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(UniquenessCheckResultSuccessImpl(Instant.now()))
    }

    private fun mockErrorUniquenessClientService(
        errorType: UniquenessCheckError
    ): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                errorType
            )
        )
    }

    private fun mockThrowErrorUniquenessCheckClientService() = mock<LedgerUniquenessCheckerClientService> {
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any()) } doThrow
                IllegalArgumentException("Uniqueness checker cannot be reached")
    }

    private fun mockUniquenessClientService(response: UniquenessCheckResult) = mock<LedgerUniquenessCheckerClientService> {
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any()) } doReturn response
    }
}
