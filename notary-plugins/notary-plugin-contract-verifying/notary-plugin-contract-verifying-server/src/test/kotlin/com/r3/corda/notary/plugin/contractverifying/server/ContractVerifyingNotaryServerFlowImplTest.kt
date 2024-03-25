package com.r3.corda.notary.plugin.contractverifying.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionInvalidSignature
import com.r3.corda.notary.plugin.common.NotaryExceptionReferenceStateUnknown
import com.r3.corda.notary.plugin.common.NotaryExceptionTimeWindowOutOfBounds
import com.r3.corda.notary.plugin.common.NotaryExceptionTransactionVerificationFailure
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.common.testkit.generateCompositeKey
import net.corda.ledger.common.testkit.generatePublicKey
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionAndSignaturesImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorUnhandledExceptionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionGeneral
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
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Suppress("MaxLineLength")
@TestInstance(PER_CLASS)
class ContractVerifyingNotaryServerFlowImplTest {

    private companion object {
        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
        const val NOTARY_SERVICE_BACKCHAIN_REQUIRED = "corda.notary.service.backchain.required"

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
    private val mockDependencyInvalidOutputStateAndRef = mock<StateAndRef<*>>().also {
        whenever(it.ref).thenReturn(INVALID_TX_OUTPUT)
    }
    private val mockDependencyOutputStateAndRef1 = mock<StateAndRef<*>>().also {
        whenever(it.ref).thenReturn(SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_1)
    }
    private val mockDependencyOutputStateAndRef2 = mock<StateAndRef<*>>().also {
        whenever(it.ref).thenReturn(SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_2)
    }
    private val mockDependencyOutputStateAndRef3 = mock<StateAndRef<*>>().also {
        whenever(it.ref).thenReturn(SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1)
    }
    private val mockDependencyOutputStateAndRef4 = mock<StateAndRef<*>>().also {
        whenever(it.ref).thenReturn(SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2)
    }
    private val filteredTxOutputData = mapOf(
        0 to mockDependencyOutputStateAndRef1,
        1 to mockDependencyOutputStateAndRef2,
        2 to mockDependencyOutputStateAndRef3,
        3 to mockDependencyOutputStateAndRef4
    )
    private val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>>().also {
        whenever(it.values).thenReturn(filteredTxOutputData)
    }

    // Notary vnodes
    private val notaryVNodeAliceKey = generatePublicKey()
    private val notaryVNodeBobKey = generatePublicKey()
    private val invalidVnodeKey = generatePublicKey()

    // Signatory vnodes
    private val signatoryVnodeCharlieKey = generatePublicKey()

    private val notarySignatureAlice = getSignatureWithMetadataExample(notaryVNodeAliceKey)
    private val notarySignatureBob = getSignatureWithMetadataExample(notaryVNodeBobKey)

    private val signatorySignatureCharlie = getSignatureWithMetadataExample(signatoryVnodeCharlieKey)

    private val notaryServiceCompositeKey = generateCompositeKey(notaryVNodeAliceKey to notaryVNodeBobKey)

    // Member names
    private val notaryServiceName = MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")
    private val notaryAliceName = MemberX500Name("Alice", "London", "GB")
    private val memberCharlieName = MemberX500Name.parse("O=MemberCharlie, L=London, C=GB")

    private val memberProvidedContext = mock<MemberContext>()
    private val mockMemberLookup = mock<MemberLookup>()
    private val notaryMemberInfo = mock<MemberInfo>()

    private val notaryInfo = mock<NotaryInfo>()

    private val signedTransaction = mock<UtxoSignedTransaction>()
    private val filteredTransaction = mock<UtxoFilteredTransaction>()

    private val filteredTxAndSignature = UtxoFilteredTransactionAndSignaturesImpl(
        filteredTransaction,
        listOf(notarySignatureAlice)
    )
    private val filteredTxsAndSignatures = listOf(filteredTxAndSignature)

    private val transactionMetadata = mock<TransactionMetadata>()
    private val mockTimeWindow = mock<TimeWindow>().also {
        whenever(it.from).thenReturn(Instant.now())
        whenever(it.until).thenReturn(Instant.now().plusMillis(100000))
    }

    private val mockOutputStateAndRef = mock<StateAndRef<*>>().also {
        whenever(it.ref).thenReturn(SIGNED_TX_OUTPUT)
    }

    private val mockTransactionSignatureService = mock<TransactionSignatureServiceInternal>()
    private val mockLedgerService = mock<UtxoLedgerService>()
    private val mockNotarySignatureVerificationService = mock<NotarySignatureVerificationService>()

    // cache for storing response from server
    private val responseFromServer = mutableListOf<NotarizationResponse>()

    private val session = mock<FlowSession>()

    @BeforeEach
    fun beforeEach() {
        responseFromServer.clear()
        reset(mockLedgerService, signedTransaction, filteredTransaction)
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

        // Signed transaction
        whenever(signedTransaction.id).thenReturn(SIGNED_TX_ID)
        whenever(signedTransaction.inputStateRefs).thenReturn(
            listOf(
                SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_1,
                SIGNED_TX_INPUT_DEPENDENCY_STATE_REF_2
            )
        )
        whenever(signedTransaction.referenceStateRefs).thenReturn(
            listOf(
                SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_1,
                SIGNED_TX_INPUT_REFERENCE_DEPENDENCY_STATE_REF_2
            )
        )
        whenever(signedTransaction.outputStateAndRefs).thenReturn(listOf(mockOutputStateAndRef))
        whenever(signedTransaction.timeWindow).thenReturn(mockTimeWindow)
        whenever(signedTransaction.notaryName).thenReturn(notaryServiceName)
        whenever(signedTransaction.notaryKey).thenReturn(notaryServiceCompositeKey)
        whenever(signedTransaction.metadata).thenReturn(transactionMetadata)
        whenever(signedTransaction.verifySignatorySignatures()).thenAnswer { }

        // Filtered transaction
        whenever(filteredTransaction.id).thenReturn(FILTERED_TX_ID)
        whenever(filteredTransaction.outputStateAndRefs).thenReturn(mockOutputStateRefUtxoFilteredData)
        whenever(filteredTransaction.notaryName).thenReturn(notaryServiceName)
        whenever(filteredTransaction.timeWindow).thenReturn(mockTimeWindow)
        whenever(filteredTransaction.verify()).thenAnswer { }

        whenever(
            mockTransactionSignatureService.getIdOfPublicKey(
                signedTransaction.notaryKey,
                DigestAlgorithmName.SHA2_256.name
            )
        ).thenReturn(notarySignatureAlice.by)

        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(any(), any(), any(), any())).doAnswer { }

        // Get current notary and parse its data
        whenever(mockMemberLookup.myInfo()).thenReturn(notaryMemberInfo)
        whenever(notaryMemberInfo.name).thenReturn(notaryAliceName)
        whenever(notaryMemberInfo.memberProvidedContext).thenReturn(memberProvidedContext)
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_NAME, MemberX500Name::class.java)).thenReturn(
            notaryServiceName
        )
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_BACKCHAIN_REQUIRED, Boolean::class.java)).thenReturn(false)

        whenever(notaryInfo.name).thenReturn(notaryAliceName)
        whenever(notaryInfo.publicKey).thenReturn(notaryVNodeAliceKey)

        // Session
        whenever(session.send(any())).thenAnswer {
            responseFromServer.add(it.arguments.first() as NotarizationResponse)
            Unit
        }
        whenever(session.counterparty).thenReturn(memberCharlieName)
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary should respond with error if no keys found for signing`(notarizationType: NotarizationType) {
        // We sign with a key that is not part of the notary composite key
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenThrow(
            TransactionNoAvailableKeysException("The publicKeys do not have any private counterparts available.", null)
        )
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseFromServer.first().signatures).isEmpty()
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).message)
            .contains("The publicKeys do not have any private counterparts available.")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with error if request signature is invalid`(notarizationType: NotarizationType) {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notarySignatureAlice))
        )
        whenever(filteredTransaction.notaryKey).thenReturn(invalidVnodeKey)
        val filteredTransactionSignatures = UtxoFilteredTransactionAndSignaturesImpl(
            filteredTransaction,
            listOf(notarySignatureAlice)
        )
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java)).thenReturn(
            ContractVerifyingNotarizationPayload(
                signedTransaction,
                listOf(filteredTransactionSignatures),
                notarizationType
            )
        )
        whenever(mockNotarySignatureVerificationService.verifyNotarySignatures(any(), any(), any(), any()))
            .thenThrow(IllegalArgumentException("DUMMY ERROR"))

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionInvalidSignature::class.java)
        assertThat((responseError as NotaryExceptionInvalidSignature).message)
            .contains("A valid notary signature is not found with error message: DUMMY ERROR")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with error if the uniqueness check fails`(notarizationType: NotarizationType) {
        val unknownStateRef = UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0)

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(
            mockErrorUniquenessClientService(
                UniquenessCheckErrorReferenceStateUnknownImpl(listOf(unknownStateRef)),
                notarizationType
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

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with error if an error encountered during uniqueness check`(notarizationType: NotarizationType) {
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(mockThrowErrorUniquenessCheckClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).message)
            .contains("Error during notarization. Cause: Uniqueness checker cannot be reached")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with signatures if alice key is in composite key`(notarizationType: NotarizationType) {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notarySignatureAlice))
        )
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val response = responseFromServer.first()
        assertThat(response.error).isNull()
        assertThat(response.signatures).hasSize(1)
        assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey.fullIdHash())
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with signatures if bob key is in composite key`(notarizationType: NotarizationType) {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notarySignatureBob))
        )
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val response = responseFromServer.first()
        assertThat(response.error).isNull()
        assertThat(response.signatures).hasSize(1)
        assertThat(response.signatures.first().by).isEqualTo(notaryVNodeBobKey.fullIdHash())
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with error if time window is not valid`(notarizationType: NotarizationType) {
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(
            mockErrorUniquenessClientService(
                UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
                    Clock.systemUTC().instant(),
                    Instant.now().plus(10, ChronoUnit.DAYS),
                    Instant.now().plus(5, ChronoUnit.DAYS)
                ),
                notarizationType
            )
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTimeWindowOutOfBounds::class.java)
        assertThat((responseError as NotaryExceptionTimeWindowOutOfBounds).notaryErrorMessage).contains(
            "Time Window Out of Bounds."
        )
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with error if output states are not audit type in the filtered tx`(
        notarizationType: NotarizationType
    ) {
        @Suppress("unchecked_cast")
        val mockOutputStateProof = mock<UtxoFilteredData.SizeOnly<StateRef>>() as UtxoFilteredData<StateAndRef<*>>
        whenever(filteredTransaction.outputStateAndRefs).thenReturn(mockOutputStateProof)
        val filteredTransactionsAndSignatures = UtxoFilteredTransactionAndSignaturesImpl(
            filteredTransaction,
            listOf(notarySignatureAlice)
        )

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java)).thenReturn(
            ContractVerifyingNotarizationPayload(signedTransaction, listOf(filteredTransactionsAndSignatures), notarizationType)
        )

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).message).contains(
            "Transaction failed to verify"
        )
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with error if Merkle proof verification of dependencies fails`(
        notarizationType: NotarizationType
    ) {
        whenever(filteredTransaction.verify()).thenThrow(IllegalArgumentException("DUMMY ERROR"))
        val filteredTransactionsAndSignatures = UtxoFilteredTransactionAndSignaturesImpl(
            filteredTransaction,
            listOf(notarySignatureAlice)
        )

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java)).thenReturn(
            ContractVerifyingNotarizationPayload(signedTransaction, listOf(filteredTransactionsAndSignatures), notarizationType)
        )

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).message)
            .contains("Error during notarization. Cause: DUMMY ERROR")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should throw general error when unhandled exception in uniqueness checker`(notarizationType: NotarizationType) {
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(
            mockErrorUniquenessClientService(
                UniquenessCheckErrorUnhandledExceptionImpl(
                    IllegalArgumentException::class.java.name,
                    "Unhandled error!"
                ),
                notarizationType
            )
        )
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).message)
            .contains(
                "Unhandled exception of type java.lang.IllegalArgumentException encountered during " +
                    "uniqueness checking with message: Unhandled error!"
            )
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin server should respond with error if notary identity invalid`(notarizationType: NotarizationType) {
        whenever(signedTransaction.notaryName).thenReturn(MemberX500Name.parse("C=GB,L=London,O=Bob"))
        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java)).thenReturn(
            ContractVerifyingNotarizationPayload(signedTransaction, listOf(filteredTxAndSignature), notarizationType)
        )

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        assertThat(responseFromServer).hasSize(1)

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).message)
            .contains("does not match the notary service represented by this notary virtual node")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary should respond with error if dependency input is missing`(notarizationType: NotarizationType) {
        val filteredTxOutputData = mapOf(0 to mockDependencyOutputStateAndRef1)
        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>>().also {
            whenever(it.values).thenReturn(filteredTxOutputData)
        }

        whenever(filteredTransaction.outputStateAndRefs).thenReturn(mockOutputStateRefUtxoFilteredData)

        val filteredTransactionsAndSignatures = UtxoFilteredTransactionAndSignaturesImpl(
            filteredTransaction,
            listOf(notarySignatureAlice)
        )

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java)).thenReturn(
            ContractVerifyingNotarizationPayload(signedTransaction, listOf(filteredTransactionsAndSignatures), notarizationType)
        )

        callServer(mockSuccessfulUniquenessClientService(notarizationType))

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).message)
            .contains("Missing input state and ref from the filtered transaction")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary should respond with error if dependency reference is missing`(notarizationType: NotarizationType) {
        val filteredTxOutputData = mapOf(
            0 to mockDependencyOutputStateAndRef1,
            1 to mockDependencyOutputStateAndRef2,
            2 to mockDependencyOutputStateAndRef3
        )
        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>>().also {
            whenever(it.values).thenReturn(filteredTxOutputData)
        }

        whenever(filteredTransaction.outputStateAndRefs).thenReturn(mockOutputStateRefUtxoFilteredData)

        val filteredTransactionsAndSignatures = UtxoFilteredTransactionAndSignaturesImpl(
            filteredTransaction,
            listOf(notarySignatureAlice)
        )

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java)).thenReturn(
            ContractVerifyingNotarizationPayload(signedTransaction, listOf(filteredTransactionsAndSignatures), notarizationType)
        )

        callServer(mockSuccessfulUniquenessClientService(notarizationType))

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).message)
            .contains("Missing reference state and ref from the filtered transaction")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary should respond with error if dependency is invalid`(notarizationType: NotarizationType) {
        val filteredTxOutputData = mapOf(
            0 to mockDependencyOutputStateAndRef1,
            1 to mockDependencyOutputStateAndRef2,
            2 to mockDependencyOutputStateAndRef3,
            3 to mockDependencyInvalidOutputStateAndRef
        )
        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>>().also {
            whenever(it.values).thenReturn(filteredTxOutputData)
        }

        whenever(filteredTransaction.outputStateAndRefs).thenReturn(mockOutputStateRefUtxoFilteredData)

        val filteredTransactionsAndSignatures = UtxoFilteredTransactionAndSignaturesImpl(
            filteredTransaction,
            listOf(notarySignatureAlice)
        )

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java)).thenReturn(
            ContractVerifyingNotarizationPayload(signedTransaction, listOf(filteredTransactionsAndSignatures), notarizationType)
        )

        callServer(mockSuccessfulUniquenessClientService(notarizationType))

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).message)
            .contains("Missing reference state and ref from the filtered transaction")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary should respond with error if a signed transaction failed to verify signatory signature`(notarizationType: NotarizationType) {
        whenever(signedTransaction.verifySignatorySignatures()).thenThrow(NotaryExceptionGeneral("Signature failed error", null))

        callServer(mockSuccessfulUniquenessClientService(notarizationType))

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
        assertThat((responseError as NotaryExceptionGeneral).message)
            .contains("Signature failed error")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary should respond with error if contract verification failed`(notarizationType: NotarizationType) {
        whenever(
            mockLedgerService.verify(
                signedTransaction.toLedgerTransaction(
                    listOf(mockDependencyOutputStateAndRef1, mockDependencyOutputStateAndRef2),
                    listOf(mockDependencyOutputStateAndRef3, mockDependencyOutputStateAndRef4)
                )
            )
        ).thenThrow(NotaryExceptionTransactionVerificationFailure("contract verification failed"))

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(mockSuccessfulUniquenessClientService(notarizationType))

        val responseError = responseFromServer.first().error
        assertThat(responseError).isNotNull
        assertThat(responseError).isInstanceOf(NotaryExceptionTransactionVerificationFailure::class.java)
        assertThat((responseError as NotaryExceptionTransactionVerificationFailure).message)
            .contains("Transaction failed to verify with error message", "contract verification failed")
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary should successfully notarise if a signed tx verifies signatory signatures`(notarizationType: NotarizationType) {
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(signatorySignatureCharlie))
        )

        whenever(mockLedgerService.verify(any())).thenAnswer { }

        whenever(signedTransaction.verifySignatorySignatures()).thenAnswer { }

        whenever(session.receive(ContractVerifyingNotarizationPayload::class.java))
            .thenReturn(ContractVerifyingNotarizationPayload(signedTransaction, filteredTxsAndSignatures, notarizationType))

        callServer(mockSuccessfulUniquenessClientService(notarizationType))
        val response = responseFromServer.first()
        assertThat(response.error).isNull()
    }

    @Suppress("LongParameterList")
    private fun callServer(clientService: LedgerUniquenessCheckerClientService) {
        val server = ContractVerifyingNotaryServerFlowImpl(
            clientService,
            mockTransactionSignatureService,
            mockLedgerService,
            mockMemberLookup,
            mockNotarySignatureVerificationService
        )

        server.call(session)
    }

    private fun mockSuccessfulUniquenessClientService(notarizationType: NotarizationType): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(UniquenessCheckResultSuccessImpl(Instant.now()), notarizationType)
    }

    private fun mockErrorUniquenessClientService(
        errorType: UniquenessCheckError,
        notarizationType: NotarizationType
    ): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                errorType
            ),
            notarizationType
        )
    }

    private fun mockThrowErrorUniquenessCheckClientService(notarizationType: NotarizationType) =
        mock<LedgerUniquenessCheckerClientService> {
            when (notarizationType) {
                NotarizationType.WRITE -> {
                    on { requestUniquenessCheckWrite(any(), any(), any(), any(), any(), any(), any()) } doThrow
                        IllegalArgumentException("Uniqueness checker cannot be reached")
                    on { requestUniquenessCheckRead(any(), any(), any(), any()) } doThrow
                        RuntimeException("Wrong uniqueness check type method called")
                }
                NotarizationType.READ -> {
                    on { requestUniquenessCheckWrite(any(), any(), any(), any(), any(), any(), any()) } doThrow
                        RuntimeException("Wrong uniqueness check type method called")
                    on { requestUniquenessCheckRead(any(), any(), any(), any()) } doThrow
                        IllegalArgumentException("Uniqueness checker cannot be reached")
                }
            }
        }

    private fun mockUniquenessClientService(response: UniquenessCheckResult, notarizationType: NotarizationType) =
        mock<LedgerUniquenessCheckerClientService> {
            when (notarizationType) {
                NotarizationType.WRITE -> {
                    on { requestUniquenessCheckWrite(any(), any(), any(), any(), any(), any(), any()) } doReturn response
                    on { requestUniquenessCheckRead(any(), any(), any(), any()) } doThrow
                        AssertionError("Wrong uniqueness check type method called")
                }
                NotarizationType.READ -> {
                    on { requestUniquenessCheckWrite(any(), any(), any(), any(), any(), any(), any()) } doThrow
                        AssertionError("Wrong uniqueness check type method called")
                    on { requestUniquenessCheckRead(any(), any(), any(), any()) } doReturn response
                }
            }
        }
}