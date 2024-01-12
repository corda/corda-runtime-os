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
import net.corda.v5.crypto.SecureHash
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
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.TestMethodOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ContractVerifyingNotaryServerFlowImplTest {

    private companion object {
        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
        const val NOTARY_SERVICE_BACKCHAIN_REQUIRED = "corda.notary.service.backchain.required"
        const val OUTPUT_STATE_AND_REFS = "outputStateRefs"
        const val INPUT_STATE_AND_REFS = "inputStateRefs"
        const val NOTARY_NAME = "notaryName"
        const val NOTARY_KEY = "notaryKey"
        const val TXID = "id"
        const val TIME_WINDOW = "timeWindow"
        const val REFSTATE_REFS = "referenceStateRefs"
        const val METADATA = "metadata"
        const val VERIFY_SIGNATORY_SIGNATURES = "VERIFY_SIGNATORY_SIGNATURES"
        const val MOCK_THROW = "MOCK_THROW"

        val SIGNED_TX_ID = SecureHashUtils.randomSecureHash()
        val FILTERED_TX_ID = SecureHashUtils.randomSecureHash()
        val INVALID_TX_ID = SecureHashUtils.randomSecureHash()
        val SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_1 = StateRef(FILTERED_TX_ID, 0)
        val SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_2 = StateRef(FILTERED_TX_ID, 1)

        val SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1 = StateRef(FILTERED_TX_ID, 2)
        val SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2 = StateRef(FILTERED_TX_ID, 3)
        val SIGNED_TX_OUTPUT = StateRef(SIGNED_TX_ID, 0)
        val INVALID_TX_OUTPUT = StateRef(INVALID_TX_ID, 4)
    }

    // default mock for filtered transaction
    private val mockDependencyInvalidOutputStateAndRef = mock<StateAndRef<*>> {
        on { ref } doReturn INVALID_TX_OUTPUT
    }
    private val mockDependencyOutputStateAndRef1 = mock<StateAndRef<*>> {
        on { ref } doReturn SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_1
    }
    private val mockDependencyOutputStateAndRef2 = mock<StateAndRef<*>> {
        on { ref } doReturn SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_2
    }
    private val mockDependencyOutputStateAndRef3 = mock<StateAndRef<*>> {
        on { ref } doReturn SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1
    }
    private val mockDependencyOutputStateAndRef4 = mock<StateAndRef<*>> {
        on { ref } doReturn SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2
    }
    private val filteredTxOutputData = mapOf(
        0 to mockDependencyOutputStateAndRef1,
        1 to mockDependencyOutputStateAndRef2,
        2 to mockDependencyOutputStateAndRef3,
        3 to mockDependencyOutputStateAndRef4
    )
    private val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>> {
        on { values } doReturn filteredTxOutputData
    }

    // Notary vnodes
    private val notaryVNodeAliceKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val notaryVNodeBobKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val invalidVnodeKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x03)) }

    // Signatory vnodes
    private val signatoryVnodeCharlieKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x04)) }

    // Notary signatures
    private val notarySignatureAlice = getSignatureWithMetadataExample(notaryVNodeAliceKey)
    private val notarySignatureBob = getSignatureWithMetadataExample(notaryVNodeBobKey)

    // Signatory signatures
    private val signatorySignatureCharlie = getSignatureWithMetadataExample(signatoryVnodeCharlieKey)

    // Notary Service and key
    private val notaryServiceCompositeKey = mock<CompositeKey> {
        on { leafKeys } doReturn setOf(notaryVNodeAliceKey, notaryVNodeBobKey)
    }

    // member names
    private val notaryServiceName = MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")
    private val notaryAliceName = MemberX500Name("Alice", "London", "GB")

    // The client that initiated the session with the notary server
    private val memberCharlieName = MemberX500Name.parse("O=MemberCharlie, L=London, C=GB")

    // mock for notary member lookup
    private val memberProvidedContext = mock<MemberContext>()
    private val mockMemberLookup = mock<MemberLookup>()
    private val notaryInfoAlice = mock<MemberInfo>().also {
        whenever(it.name).thenReturn(notaryAliceName)
        whenever(it.memberProvidedContext).thenReturn(memberProvidedContext)
    }

    // Prepare filteredTransactionAndSignature data
    private val filteredTxAndSignature = FilteredTransactionAndSignatures(
        mockFilteredTransaction(),
        listOf(notarySignatureAlice)
    )
    private val filteredTxsAndSignatures = listOf(
        filteredTxAndSignature
    )

    // mock fields for signed transaction
    private val transactionMetadata = mock<TransactionMetadata>()
    private val mockTimeWindow = mock<TimeWindow> {
        on { from } doReturn Instant.now()
        on { until } doReturn Instant.now().plusMillis(100000)
    }

    private val mockOutputStateAndRef = mock<StateAndRef<*>>().also {
        whenever(it.ref).thenReturn(SIGNED_TX_OUTPUT)
    }

    // mock services
    private val mockTransactionSignatureService = mock<TransactionSignatureServiceInternal>()
    private val mockLedgerService = mock<UtxoLedgerService>()

    // Cache for storing response from server
    private val responseFromServer = mutableListOf<NotarizationResponse>()

    @BeforeEach
    fun beforeEach() {
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

        // Get current notary and parse its data
        whenever(mockMemberLookup.myInfo()).thenReturn(notaryInfoAlice)
        whenever(notaryInfoAlice.memberProvidedContext).thenReturn(memberProvidedContext)
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_NAME, MemberX500Name::class.java)).thenReturn(
            notaryServiceName
        )
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_BACKCHAIN_REQUIRED, Boolean::class.java)).thenReturn(false)
    }

    @Test
    @Order(0)
    fun `Contract verifying notary should respond with error if no keys found for signing`() {
        // We sign with a key that is not part of the notary composite key
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenThrow(
            TransactionNoAvailableKeysException("The publicKeys do not have any private counterparts available.", null)
        )
        createAndCallServer(mockSuccessfulUniquenessClientService())
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseFromServer.first().signatures).isEmpty()
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText)
            .contains("The publicKeys do not have any private counterparts available.")
    }

    @Test
    @Order(1)
    fun `Contract verifying notary plugin server should respond with error if request signature is invalid`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notarySignatureAlice))
        )
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf("notaryKey" to invalidVnodeKey)),
            listOf(notarySignatureAlice)
        )
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature),
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
    @Order(2)
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
    @Order(3)
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
    @Order(4)
    fun `Contract verifying notary plugin server should respond with signatures if alice key is in composite key`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notarySignatureAlice))
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
    @Order(5)
    fun `Contract verifying notary plugin server should respond with signatures if bob key is in composite key`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notarySignatureBob))
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
    @Order(6)
    fun `Contract verifying notary plugin server should respond with error if time window not present on filtered tx`() {
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(TIME_WINDOW to null)),
            listOf(notarySignatureAlice)
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
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
    @Order(7)
    fun `Contract verifying notary plugin server should respond with error if notary name not present on filtered tx`() {
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(NOTARY_NAME to null)),
            listOf(notarySignatureAlice)
        )
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
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
    @Order(8)
    fun `Contract verifying notary plugin server should respond with error if notary key not present on filtered tx`() {
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(NOTARY_KEY to null)),
            listOf(notarySignatureAlice)
        )
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
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
    @Order(9)
    fun `Contract verifying notary plugin server should respond with error if output states are not audit type in the filtered tx`() {
        val mockOutputStateProof = mock<UtxoFilteredData.SizeOnly<StateRef>>()
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(OUTPUT_STATE_AND_REFS to mockOutputStateProof)),
            listOf(notarySignatureAlice)
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
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
    @Order(10)
    fun `Contract verifying notary plugin server should respond with error if transaction verification fails`() {
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(txVerificationLogic = ::throwVerify),
            listOf(notarySignatureAlice)
        )
        createAndCallServer(mockSuccessfulUniquenessClientService(), filteredTransactionAndSigs = listOf(filteredTxAndSignature))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText)
            .contains("Error during notarization. Cause: DUMMY ERROR")

    }

    @Test
    @Order(11)
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
    @Order(12)
    fun `Contract verifying notary plugin server should respond with error if notary identity invalid`() {
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(NOTARY_NAME to MemberX500Name.parse("C=GB,L=London,O=Bob"))),
            listOf(notarySignatureAlice)
        )
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).errorText)
            .contains("Error during notarization.")

    }

    @Test
    @Order(13)
    fun `Contract verifying notary should respond with error if dependency input is missing`() {
         val filteredTxOutputData = mapOf(0 to mockDependencyOutputStateAndRef1)
         val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>> {
            on { values } doReturn filteredTxOutputData
        }
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(OUTPUT_STATE_AND_REFS to mockOutputStateRefUtxoFilteredData)),
            listOf(notarySignatureAlice)
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
        )

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).errorText)
            .contains("Missing input state and ref from the filtered transaction")
    }

    @Test
    @Order(14)
    fun `Contract verifying notary should respond with error if dependency reference is missing`() {
        val filteredTxOutputData = mapOf(
            0 to mockDependencyOutputStateAndRef1,
            1 to mockDependencyOutputStateAndRef2,
            2 to mockDependencyOutputStateAndRef3
        )
        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>> {
            on { values } doReturn filteredTxOutputData
        }

        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(OUTPUT_STATE_AND_REFS to mockOutputStateRefUtxoFilteredData)),
            listOf(notarySignatureAlice)
        )
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
        )

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).errorText)
            .contains("Missing reference state and ref from the filtered transaction")
    }

    @Test
    @Order(15)
    fun `Contract verifying notary should respond with error if dependency is invalid`() {
        val filteredTxOutputData = mapOf(
            0 to mockDependencyOutputStateAndRef1,
            1 to mockDependencyOutputStateAndRef2,
            2 to mockDependencyOutputStateAndRef3,
            3 to mockDependencyInvalidOutputStateAndRef
        )
        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>> {
            on { values } doReturn filteredTxOutputData
        }
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(OUTPUT_STATE_AND_REFS to mockOutputStateRefUtxoFilteredData)),
            listOf(notarySignatureAlice)
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
        )

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).errorText)
            .contains("Missing reference state and ref from the filtered transaction")
    }

    @Test
    @Order(16)
    fun `Contract verifying notary should respond with error if either current or filtered tx is invalid`() {
        val filteredTxAndSignature = FilteredTransactionAndSignatures(
            mockFilteredTransaction(mapOf(TXID to SIGNED_TX_ID)),
            listOf(notarySignatureAlice)
        )
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTransactionAndSigs = listOf(filteredTxAndSignature)
        )

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).errorText)
            .contains("Either filtered transaction \"$SIGNED_TX_ID\" or current transaction \"$SIGNED_TX_ID\" is invalid")
    }

    @Test
    @Order(17)
    fun `Contract verifying notary should respond with error if a signed transaction failed to signatory signature verification`() {
        val signedTransaction = mockSignedTransaction(mapOf(VERIFY_SIGNATORY_SIGNATURES to MOCK_THROW))

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            signedTx = signedTransaction
        )
        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).message)
            .contains("Signature failed error")
    }

    @Test
    @Order(Integer.MAX_VALUE)
    fun `Contract verifying notary should respond with error if contract verification failed`() {
        val signedTransaction = mockSignedTransaction()

        whenever(
            mockLedgerService.verify(
                signedTransaction.toLedgerTransaction(
                    listOf(
                        mockDependencyOutputStateAndRef1,
                        mockDependencyOutputStateAndRef2
                    ), listOf(mockDependencyOutputStateAndRef3, mockDependencyOutputStateAndRef4)
                )
            )
        ).thenThrow(NotaryExceptionTransactionVerificationFailure("contract verification failed"))
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            signedTx = signedTransaction
        )
        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).message)
            .contains("Transaction failed to verify with error message", "contract verification failed")
    }

    // to remove --------------------------------------------------
    @Test
    @Order(19)
    fun `Contract verifying notary should successfully verify signatories if a signed tx has all required signatures`() {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(signatorySignatureCharlie))
        )

        val signedTransaction = mockSignedTransaction()
        whenever(signedTransaction.verifySignatorySignatures()).thenAnswer {  }

        signedTransaction.verifySignatorySignatures()
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            signedTx = signedTransaction
        )

        val response = responseFromServer.first()
        assertThat(response.error).isNull()
    }

    private fun mockSignedTransaction(signedTxContents: Map<String, Any?> = emptyMap()): UtxoSignedTransaction {
        val mockThrow = signedTxContents.containsKey(VERIFY_SIGNATORY_SIGNATURES) &&
                signedTxContents[VERIFY_SIGNATORY_SIGNATURES] == MOCK_THROW

        return mock<UtxoSignedTransaction> {
            on { id } doAnswer {
                if (signedTxContents.containsKey(TXID)) signedTxContents[TXID] as SecureHash else SIGNED_TX_ID
            }
            on { inputStateRefs } doAnswer {
                @Suppress("unchecked_cast")
                if (signedTxContents.containsKey(INPUT_STATE_AND_REFS)) signedTxContents[INPUT_STATE_AND_REFS] as List<StateRef>
                else listOf(SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_1, SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_2)
            }
            on { referenceStateRefs } doAnswer {
                @Suppress("unchecked_cast")
                if (signedTxContents.containsKey(REFSTATE_REFS)) signedTxContents[REFSTATE_REFS] as List<StateRef>
                else listOf(
                    SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1,
                    SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2
                )
            }
            on { outputStateAndRefs } doAnswer {
                @Suppress("unchecked_cast")
                if (signedTxContents.containsKey(OUTPUT_STATE_AND_REFS))
                    signedTxContents[OUTPUT_STATE_AND_REFS] as List<StateAndRef<*>>
                else listOf(mockOutputStateAndRef)
            }
            on { timeWindow } doAnswer {
                if (signedTxContents.containsKey(TIME_WINDOW)) signedTxContents[TIME_WINDOW] as TimeWindow?
                else mockTimeWindow
            }
            on { notaryKey } doAnswer {
                if (signedTxContents.containsKey(NOTARY_KEY)) signedTxContents[NOTARY_KEY] as PublicKey?
                else notaryServiceCompositeKey
            }
            on { notaryName } doAnswer {
                if (signedTxContents.containsKey(NOTARY_NAME)) signedTxContents[NOTARY_NAME] as MemberX500Name?
                else notaryServiceName
            }
            on { metadata } doAnswer {
                if (signedTxContents.containsKey(METADATA)) signedTxContents[METADATA] as TransactionMetadata?
                else transactionMetadata
            }
            if (mockThrow) {
                on { verifySignatorySignatures() } doThrow (NotaryExceptionGeneral("Signature failed error"))
            }
        }
    }

    private fun mockFilteredTransaction(
        filteredTxContents: Map<String, Any?> = emptyMap(),
        txVerificationLogic: () -> Unit = {},
    ): UtxoFilteredTransaction {
        return mock<UtxoFilteredTransaction> {
            on { id } doAnswer {
                if (filteredTxContents.containsKey(TXID)) filteredTxContents[TXID] as SecureHash
                else FILTERED_TX_ID
            }
            on { outputStateAndRefs } doAnswer {
                @Suppress("unchecked_cast")
                filteredTxContents[OUTPUT_STATE_AND_REFS] as? UtxoFilteredData<StateAndRef<*>>
                    ?: mockOutputStateRefUtxoFilteredData
            }
            on { notaryName } doAnswer {
                if (filteredTxContents.containsKey(NOTARY_NAME)) filteredTxContents[NOTARY_NAME] as MemberX500Name?
                else notaryServiceName

            }
            on { notaryKey } doAnswer {
                if (filteredTxContents.containsKey("notaryKey")) filteredTxContents["notaryKey"] as PublicKey?
                 else notaryServiceCompositeKey
            }
            on { timeWindow } doAnswer {
                if (filteredTxContents.containsKey("timeWindow")) filteredTxContents["timeWindow"] as TimeWindow?
                 else mockTimeWindow
            }
            on { verify() } doAnswer {
                txVerificationLogic()
            }
        }
    }


    /**
     *  This function mocks up data such as filtered tx, signed tx and signatures to test verifying notary server.
     *
     *  @param clientService {@link LedgerUniquenessCheckerClientService} mock with UniquenessCheckResult that will be either
     *  - UniquenessCheckResultFailureImpl using [mockErrorUniquenessClientService]
     *  - UniquenessCheckResultSuccessImpl using [mockSuccessfulUniquenessClientService]
     *  @param notarySignature DigitalSignatureAndMetadata of notary
     *  @param signedTx {@link UtxoSignedTransaction} mock to use in test
     *  @param filteredTransactionAndSigs a list of {@link FilteredTransactionAndSignatures}
     *  dependency's transaction and its notary signatures
     *  @param signatureVerificationLogic lambda function to execute on
     *  UtxoFilteredTransaction.verifyAttachedNotarySignature() call
     * */
    @Suppress("LongParameterList")
    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        notarySignature: DigitalSignatureAndMetadata = notarySignatureAlice,
        signedTx: UtxoSignedTransaction = mockSignedTransaction(),
        filteredTransactionAndSigs: List<FilteredTransactionAndSignatures> = filteredTxsAndSignatures,
        signatureVerificationLogic: () -> Unit = {}
    ) {
        whenever(
            mockTransactionSignatureService.getIdOfPublicKey(
                signedTx.notaryKey,
                DigestAlgorithmName.SHA2_256.name
            )
        ).thenReturn(notarySignature.by)

        val mockNotarySignatureVerificationService = mock<NotarySignatureVerificationService> {
            on { verifyNotarySignatures(any(), any(), any(), any()) } doAnswer { signatureVerificationLogic() }
        }

        // Mock the receive and send from the counterparty session, unless it is overwritten
        val paramOrDefaultSession = mock<FlowSession> {
            on { receive(ContractVerifyingNotarizationPayload::class.java) } doReturn ContractVerifyingNotarizationPayload(
                signedTx,
                filteredTransactionAndSigs
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