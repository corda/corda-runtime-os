package com.r3.corda.notary.plugin.contractverifying.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionGeneral
import com.r3.corda.notary.plugin.common.NotaryExceptionReferenceStateUnknown
import com.r3.corda.notary.plugin.contractverifying.api.ContractVerifyingNotarizationPayload
import com.r3.corda.notary.plugin.contractverifying.api.FilteredTransactionAndSignatures
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.testkit.SecureHashUtils
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
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
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


        /* Cache for storing response from server */
        val responseFromServer = mutableListOf<NotarizationResponse>()

        /* Notary VNodes */
        val notaryVNodeAliceKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01))}
        val notaryVNodeBobKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02))}

        /* Notary Service */
        val notaryServiceCompositeKey = mock<CompositeKey> {
            on { leafKeys } doReturn setOf(notaryVNodeAliceKey, notaryVNodeBobKey)
        }

        val notaryServiceName = MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")

        // The client that initiated the session with the notary server
        val memberCharlieName = MemberX500Name.parse("O=MemberCharlie, L=London, C=GB")

        // mock services
        val mockTransactionSignatureService = mock<TransactionSignatureService>()
        val mockLedgerService = mock<UtxoLedgerService>()

        // mock for notary member lookup
        val notaryInfo = mock<MemberInfo>()
        val memberProvidedContext = mock<MemberContext>()
        val mockMemberLookup = mock<MemberLookup>()
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
    fun `Contract verifying notary should respond with success if the notary key is simple`() {
        // We sign with a key that is not part of the notary composite key
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            notaryServiceKey = notaryVNodeAliceKey
        ) {
            assertThat(responseFromServer).hasSize(1)

            val response = responseFromServer.first()
            assertThat(response.error).isNull()
            assertThat(response.signatures).hasSize(1)
            assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey.fullIdHash())
        }
    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if request signature is invalid`() {
        createAndCallServer(mockSuccessfulUniquenessClientService()) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Error while processing request from client")
        }
    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if the uniqueness check fails`() {
        val unknownStateRef = UniquenessCheckStateRefImpl(SecureHashUtils.randomSecureHash(), 0)

        createAndCallServer(mockErrorUniquenessClientService(
            UniquenessCheckErrorReferenceStateUnknownImpl(listOf(unknownStateRef))
        )) {

            assertThat(responseFromServer).hasSize(1)
            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryExceptionReferenceStateUnknown::class.java)
            assertThat((responseError as NotaryExceptionReferenceStateUnknown).unknownStates).containsExactly(unknownStateRef)
        }
    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if an error encountered during uniqueness check`() {
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
    fun `Contract verifying notary plugin server should respond with signatures if the uniqueness check successful`() {
        val notaryVNodeAliceSig = getSignatureWithMetadataExample(notaryVNodeAliceKey)

        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notaryVNodeAliceSig))
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
        ) {
            assertThat(responseFromServer).hasSize(1)

            val response = responseFromServer.first()
            assertThat(response.error).isNull()
            assertThat(response.signatures).hasSize(1)
            assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey.fullIdHash())
        }
    }

    @Test
    fun `Contract verifying notary plugin server should respond with error if time window not present on filtered tx`() {
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
    fun `Contract verifying notary plugin server should respond with error if notary name not present on filtered tx`() {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("notaryName" to null)) {
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
    fun `Contract verifying notary plugin server should respond with error if notary key not present on filtered tx`() {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            filteredTxContents = mapOf("notaryKey" to null)) {
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
    fun `Contract verifying notary plugin server should respond with error if transaction verification fails`() {
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
    fun `Contract verifying notary plugin server should throw general error when unhandled exception in uniqueness checker`() {
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
    fun `Contract verifying notary plugin server should respond with error if notary identity invalid`() {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
            // What party we pass in here does not matter, it just needs to be different from the notary server party
            filteredTxContents = mapOf("notaryName" to MemberX500Name.parse("C=GB,L=London,O=Bob"))
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).errorText)
                .contains("Error while processing request from client")
        }
    }

    /**
     *  This function mocks up data such as filtered tx, signed tx and signatures to test verifying notary server.
     *
     *  @param clientService {@link LedgerUniquenessCheckerClientService} mock with UniquenessCheckResult that will be either
     *  - UniquenessCheckResultFailureImpl using [mockErrorUniquenessClientService]
     *  - UniquenessCheckResultSuccessImpl using [mockSuccessfulUniquenessClientService]
     *  @param notaryServiceKey PublicKey of notary. CompositeKey by default.
     *  @param filteredTxContents Map of content of FilteredTransaction to mock up with. It will be mocked up with default values if it's empty.
     *  filtered contests that can be in a map:
     *  - outputStateRefs
     *  - notaryName
     *  - notaryKey
     *  - timeWindow
     *  @param txVerificationLogic lambda function to execute on UtxoFilteredTransaction.verify() call
     * */
    @Suppress("LongParameterList")
    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        notaryServiceKey: PublicKey = notaryServiceCompositeKey,
        filteredTxContents: Map<String, Any?> = emptyMap(),
        txVerificationLogic: () -> Unit = {},
        extractData: (sigs: List<NotarizationResponse>) -> Unit
    ) {
        val txId = SecureHashUtils.randomSecureHash()

        // 1. Set up the defaults for the filtered transaction
        val transactionMetadata = mock<TransactionMetadata>()

        val mockTimeWindow = mock<TimeWindow> {
            on { from } doReturn Instant.now()
            on { until } doReturn Instant.now().plusMillis(100000)
        }

        val mockInputRef = mock<StateRef>()
        val mockDependencyOutputStateAndRef = mock<StateAndRef<*>> {
            on { ref } doReturn mockInputRef
        }
        val mockOutputStateAndRef = mock<StateAndRef<*>>()
        val mockOutputStateRefUtxoFilteredData = mock<UtxoFilteredData.Audit<StateAndRef<*>>> {
            on { values } doReturn mapOf(0 to mockDependencyOutputStateAndRef)
        }

        // 2. Get current notary and parse its data
        whenever(mockMemberLookup.myInfo()).thenReturn(notaryInfo)
        whenever(notaryInfo.memberProvidedContext).thenReturn(memberProvidedContext)
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_NAME, MemberX500Name::class.java)).thenReturn(notaryServiceName)
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_BACKCHAIN_REQUIRED, Boolean::class.java)).thenReturn(true)

        // 3. Check if any filtered transaction data should be overwritten
        val signedTx = mock<UtxoSignedTransaction>() {
            on { id } doReturn txId
            on { inputStateRefs } doReturn listOf(mockInputRef)
            on { referenceStateRefs } doReturn emptyList()
            on { outputStateAndRefs } doReturn listOf(mockOutputStateAndRef)
            on { timeWindow } doReturn mockTimeWindow
            on { notaryKey } doReturn notaryServiceKey
            on { notaryName } doReturn notaryServiceName
            on { metadata } doReturn transactionMetadata
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

        val signature = mock<DigitalSignatureAndMetadata>()

        val filteredTxAndSignatures = listOf(FilteredTransactionAndSignatures(
            filteredTx,
            listOf(signature))
        )

        // 4. Mock the receive and send from the counterparty session, unless it is overwritten
        val paramOrDefaultSession = mock<FlowSession> {
            on { receive(ContractVerifyingNotarizationPayload::class.java) } doReturn ContractVerifyingNotarizationPayload(
                signedTx,
                filteredTxAndSignatures
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
            mockMemberLookup
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
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any(), any()) } doThrow
                IllegalArgumentException("Uniqueness checker cannot be reached")
    }

    private fun mockUniquenessClientService(response: UniquenessCheckResult) = mock<LedgerUniquenessCheckerClientService> {
        on { requestUniquenessCheck(any(), any(), any(), any(), any(), any(), any()) } doReturn response
    }
}