package com.r3.corda.notary.plugin.contractverifying.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.common.NotaryExceptionInvalidSignature
import com.r3.corda.notary.plugin.common.NotaryExceptionReferenceStateUnknown
import com.r3.corda.notary.plugin.common.NotaryExceptionTransactionVerificationFailure
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import com.r3.corda.notary.plugin.contractverifying.api.FilteredTransactionAndSignatures
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorUnhandledExceptionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
class ContractVerifyingNotaryServerFlowImplTest {

    private companion object {
        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
        const val NOTARY_SERVICE_BACKCHAIN_REQUIRED = "corda.notary.service.backchain.required"
    }

    // Cache for storing response from server
    private val responseFromServer = mutableListOf<NotarizationResponse>()

    // Notary vnodes
    private val notaryVNodeAliceKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val notaryVNodeBobKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val invalidVnodeKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x03)) }

    private val signatureAlice = getSignatureWithMetadataExample(notaryVNodeAliceKey)
    private val signatureBob = getSignatureWithMetadataExample(notaryVNodeBobKey)
    private val signatureInvalid = getSignatureWithMetadataExample(invalidVnodeKey)

    // Notary Service and key
    private val notaryServiceCompositeKey = mock<CompositeKey> {
        on { leafKeys } doReturn setOf(notaryVNodeAliceKey, notaryVNodeBobKey)
    }
    private val notaryServiceName = MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")

    // The client that initiated the session with the notary server
    private val memberCharlieName = MemberX500Name.parse("O=MemberCharlie, L=London, C=GB")

    // mock services
    private val mockTransactionSignatureService = mock<TransactionSignatureServiceInternal>()
    private val mockLedgerService = mock<UtxoLedgerService>()

    // mock for notary member lookup
    private val notaryInfo = mock<MemberInfo>()
    private val memberProvidedContext = mock<MemberContext>()
    private val mockMemberLookup = mock<MemberLookup>()

    // mock fields for signed transaction
    private val txId = SecureHashUtils.randomSecureHash()
    private val transactionMetadata = mock<TransactionMetadata>()
    private val mockTimeWindow = mock<TimeWindow> {
        on { from } doReturn Instant.now()
        on { until } doReturn Instant.now().plusMillis(100000)
    }
    private  val mockInputRef = mock<StateRef>()
    private val mockOutputStateAndRef = mock<StateAndRef<*>>()

    // mock signed transaction
    private val signedTx = mock<UtxoSignedTransaction> {
        on { id } doReturn txId
        on { inputStateRefs } doReturn listOf(mockInputRef)
        on { referenceStateRefs } doReturn emptyList()
        on { outputStateAndRefs } doReturn listOf(mockOutputStateAndRef)
        on { timeWindow } doReturn mockTimeWindow
        on { notaryKey } doReturn notaryServiceCompositeKey
        on { notaryName } doReturn notaryServiceName
        on { metadata } doReturn transactionMetadata
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
    fun `Contract verifying notary should respond with error if no keys found for signing`() {
        // We sign with a key that is not part of the notary composite key
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenThrow(
            TransactionNoAvailableKeysException("The publicKeys do not have any private counterparts available.", null)
        )
        createAndCallServer(mockSuccessfulUniquenessClientService(), notarySignature = signatureInvalid)
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseFromServer.first().signatures).isEmpty()
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText)
            .contains("The publicKeys do not have any private counterparts available.")

    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if request signature is invalid`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(signatureAlice))
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("notaryKey" to invalidVnodeKey),
            signatureVerificationLogic = ::throwVerify

        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionInvalidSignature::class.java)
        assertThat((responseError as NotaryExceptionInvalidSignature).errorText)
            .contains("A valid notary signature is not found with error message: DUMMY ERROR")
    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if the uniqueness check fails`() {
        val unknownStateRef = UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0)

        createAndCallServer(
            mockErrorUniquenessClientService(
                UniquenessCheckErrorReferenceStateUnknownImpl(listOf(unknownStateRef))
            )
        )

        assertThat(responseFromServer).hasSize(1)
        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseFromServer.first().signatures).isEmpty()
        assertThat(responseError).isInstanceOf(NotaryExceptionReferenceStateUnknown::class.java)
        assertThat((responseError as NotaryExceptionReferenceStateUnknown).unknownStates).containsExactly(
            unknownStateRef
        )

    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if an error encountered during uniqueness check`() {
        createAndCallServer(mockThrowErrorUniquenessCheckClientService())
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText)
            .contains("Error during notarization. Cause: Uniqueness checker cannot be reached")

    }

    @Test
    fun `Contract verifying notary plugin server should respond with signatures if alice key is in composite key`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(signatureAlice))
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
        )
        assertThat(responseFromServer).hasSize(1)

        val response = responseFromServer.first()
        assertThat(response.error).isNull()
        assertThat(response.signatures).hasSize(1)
        assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey.fullIdHash())
    }

    @Test
    fun `Contract verifying notary plugin server should respond with signatures if bob key is in composite key`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(signatureBob))
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
        )
        assertThat(responseFromServer).hasSize(1)

        val response = responseFromServer.first()
        assertThat(response.error).isNull()
        assertThat(response.signatures).hasSize(1)
        assertThat(response.signatures.first().by).isEqualTo(notaryVNodeBobKey.fullIdHash())
    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if time window not present on filtered tx`() {
        createAndCallServer(mockSuccessfulUniquenessClientService(), filteredTxContents = mapOf("timeWindow" to null))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText).contains(
            "Error during notarization."
        )

    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if notary name not present on filtered tx`() {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("notaryName" to null)
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText).contains(
            "Error during notarization."
        )

    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if notary key not present on filtered tx`() {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("notaryKey" to null)
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText).contains(
            "Error during notarization."
        )

    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if output states are not audit type in the filtered tx`() {
        val mockOutputStateProof = mock<UtxoFilteredData.SizeOnly<StateRef>>()

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("outputStateRefs" to mockOutputStateProof)
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).errorText).contains(
            "Transaction failed to verify"
        )
    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if transaction verification fails`() {
        createAndCallServer(mockSuccessfulUniquenessClientService(), txVerificationLogic = ::throwVerify)
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionInvalidSignature::class.java)
        assertThat((responseError as NotaryExceptionInvalidSignature).errorText)
            .contains("A valid notary signature is not found")

    }

    @Test
    fun `Contract verifying notary plugin server should throw general error when unhandled exception in uniqueness checker`() {
        createAndCallServer(
            mockErrorUniquenessClientService(
                UniquenessCheckErrorUnhandledExceptionImpl(
                    IllegalArgumentException::class.java.name,
                    "Unhandled error!"
                )
            )
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText)
            .contains(
                "Unhandled exception of type java.lang.IllegalArgumentException encountered during " +
                        "uniqueness checking with message: Unhandled error!"
            )

    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if notary identity invalid`() {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            // What party we pass in here does not matter, it just needs to be different from the notary server party
            filteredTxContents = mapOf("notaryName" to MemberX500Name.parse("C=GB,L=London,O=Bob"))
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText)
            .contains("Error during notarization.")

    }


    /**
     *  This function mocks up data such as filtered tx, signed tx and signatures to test verifying notary server.
     *
     *  @param clientService {@link LedgerUniquenessCheckerClientService} mock with UniquenessCheckResult that will be either
     *  - UniquenessCheckResultFailureImpl using [mockErrorUniquenessClientService]
     *  - UniquenessCheckResultSuccessImpl using [mockSuccessfulUniquenessClientService]
     *  @param notaryServiceKey PublicKey of notary. CompositeKey by default.
     *  @param notarySignature DigitalSignatureAndMetadata of notary
     *  @param filteredTxContents Map of content of FilteredTransaction to mock up with.
     *  It will be mocked up with default values if it's empty.
     *  filtered contests that can be in a map:
     *  - outputStateRefs
     *  - notaryName
     *  - notaryKey
     *  - timeWindow
     *  @param txVerificationLogic lambda function to execute on UtxoFilteredTransaction.verify() call
     *  @param signatureVerificationLogic lambda function to execute on
     *  UtxoFilteredTransaction.verifyAttachedNotarySignature() call
     * */
    @Suppress("LongParameterList")
    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        notaryServiceKey: PublicKey = notaryServiceCompositeKey,
        notarySignature: DigitalSignatureAndMetadata = signatureAlice,
        filteredTxContents: Map<String, Any?> = emptyMap(),
        txVerificationLogic: () -> Unit = {},
        signatureVerificationLogic: () -> Unit = {}
    ) {
        // Get current notary and parse its data
        whenever(mockMemberLookup.myInfo()).thenReturn(notaryInfo)
        whenever(notaryInfo.memberProvidedContext).thenReturn(memberProvidedContext)
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_NAME, MemberX500Name::class.java)).thenReturn(
            notaryServiceName
        )
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_BACKCHAIN_REQUIRED, Boolean::class.java)).thenReturn(true)
        whenever(
            mockTransactionSignatureService.getIdOfPublicKey(
                signedTx.notaryKey,
                DigestAlgorithmName.SHA2_256.name
            )
        ).thenReturn(notarySignature.by)

        // Mock Filtered Transaction
        val mockDependencyOutputStateAndRef = mock<StateAndRef<*>> {
            on { ref } doReturn mockInputRef
        }

        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>> {
            on { values } doReturn mapOf(0 to mockDependencyOutputStateAndRef)
        }
        val filteredTx = mock<UtxoFilteredTransaction> {
            on { outputStateAndRefs } doAnswer {
                @Suppress("unchecked_cast")
                filteredTxContents["outputStateRefs"] as? UtxoFilteredData<StateAndRef<*>>
                    ?: mockOutputStateRefUtxoFilteredData
            }
            on { notaryName } doAnswer {
                if (filteredTxContents.containsKey("notaryName")) {
                    filteredTxContents["notaryName"] as MemberX500Name?
                } else {
                    notaryServiceName
                }
            }
            on { notaryKey } doAnswer {
                if (filteredTxContents.containsKey("notaryKey")) {
                    filteredTxContents["notaryKey"] as PublicKey?
                } else {
                    notaryServiceKey
                }
            }
            on { timeWindow } doAnswer {
                if (filteredTxContents.containsKey("timeWindow")) {
                    filteredTxContents["timeWindow"] as TimeWindow?
                } else {
                    mockTimeWindow
                }
            }

            on { verify() } doAnswer {
                txVerificationLogic()
            }
            on { id } doReturn txId
        }

        // Prepare filteredTransactionAndSignature data
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            filteredTx,
            listOf(notarySignature)
        )
        val filteredTxsAndSignatures = listOf(
            filteredTxAndSignature
        )

        val mockNotarySignatureVerificationService = mock<NotarySignatureVerificationService> {
            on { verifyNotarySignatures(any(), any(), any(), any()) } doAnswer { signatureVerificationLogic() }
        }

        // Mock the receive and send from the counterparty session, unless it is overwritten
        val paramOrDefaultSession = mock<FlowSession> {
            on { receive(ContractVerifyingNotarizationPayload::class.java) } doReturn ContractVerifyingNotarizationPayload(
                signedTx,
                filteredTxsAndSignatures
            )
            on { send(any()) } doAnswer {
                responseFromServer.add(it.arguments.first() as NotarizationResponse)
                Unit
            }
            on { counterparty } doReturn memberCharlieName
        }

        val server = ContractVerifyingNotaryServerFlowImpl(
            clientService,
            mockTransactionSignatureService,
            mockLedgerService,
            mockMemberLookup,
            mockNotarySignatureVerificationService
        )

        server.call(paramOrDefaultSession)
    }

    private fun throwVerify() {
        throw IllegalArgumentException("DUMMY ERROR")
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
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any(), any()) } doThrow
                IllegalArgumentException("Uniqueness checker cannot be reached")
    }

    private fun mockUniquenessClientService(response: UniquenessCheckResult) =
        mock<LedgerUniquenessCheckerClientService> {
            on { requestUniquenessCheck(any(), any(), any(), any(), any(), any(), any()) } doReturn response
        }
}