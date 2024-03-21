package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionReferenceStateUnknown
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarizationPayload
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.ledger.common.testkit.generateCompositeKey
import net.corda.ledger.common.testkit.generatePublicKey
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorUnhandledExceptionImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionGeneral
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
        const val NOTARY_SERVICE_BACKCHAIN_REQUIRED = "corda.notary.service.backchain.required"


        /* Cache for storing response from server */
        val responseFromServer = mutableListOf<NotarizationResponse>()

        /* Notary VNodes */
        val notaryVNodeAliceKey = generatePublicKey()
        val notaryVNodeBobKey = generatePublicKey()

        /* Notary Service */
        val notaryServiceCompositeKey = generateCompositeKey(notaryVNodeAliceKey to notaryVNodeBobKey)

        val notaryServiceName = MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")

        // The client that initiated the session with the notary server
        val memberCharlieName = MemberX500Name.parse("O=MemberCharlie, L=London, C=GB")

        val mockTransactionSignatureService = mock<TransactionSignatureService>()

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

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary should respond with error if no keys found for signing`(notarizationType: NotarizationType) {
        // We sign with a key that is not part of the notary composite key
        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenThrow(
            TransactionNoAvailableKeysException("The publicKeys do not have any private counterparts available.", null)
        )
        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message)
                .contains("Error while processing request from client")
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary should respond with success if the notary key is simple`(notarizationType: NotarizationType) {
        // We sign with a key that is not part of the notary composite key
        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            notaryServiceKey = notaryVNodeAliceKey,
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val response = responseFromServer.first()
            assertThat(response.error).isNull()
            assertThat(response.signatures).hasSize(1)
            assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey.fullIdHash())
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if the uniqueness check fails`(notarizationType: NotarizationType) {
        val unknownStateRef = UniquenessCheckStateRefImpl(randomSecureHash(), 0)

        createAndCallServer(
            mockErrorUniquenessClientService(
                UniquenessCheckErrorReferenceStateUnknownImpl(listOf(unknownStateRef)),
                notarizationType
            ),
            notarizationType = notarizationType
        ) {

            assertThat(responseFromServer).hasSize(1)
            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryExceptionReferenceStateUnknown::class.java)
            assertThat((responseError as NotaryExceptionReferenceStateUnknown).unknownStates).containsExactly(unknownStateRef)
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if an error encountered during uniqueness check`(notarizationType: NotarizationType) {
        createAndCallServer(mockThrowErrorUniquenessCheckClientService(notarizationType), notarizationType = notarizationType) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message)
                .contains("Error while processing request from client")
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with signatures if the uniqueness check successful`(notarizationType: NotarizationType) {
        val notaryVNodeAliceSig = getSignatureWithMetadataExample(notaryVNodeAliceKey)

        whenever(mockTransactionSignatureService.signBatch(any(), any())).thenReturn(
            listOf(listOf(notaryVNodeAliceSig))
        )

        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val response = responseFromServer.first()
            assertThat(response.error).isNull()
            assertThat(response.signatures).hasSize(1)
            assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey.fullIdHash())
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if time window not present on filtered tx`(notarizationType: NotarizationType) {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            filteredTxContents = mapOf("timeWindow" to null),
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message).contains(
                "Error while processing request from client"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if notary name not present on filtered tx`(notarizationType: NotarizationType) {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            filteredTxContents = mapOf("notaryName" to null),
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message).contains(
                "Error while processing request from client"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if notary key not present on filtered tx`(notarizationType: NotarizationType) {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            filteredTxContents = mapOf("notaryKey" to null),
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message).contains(
                "Error while processing request from client"
            )
        }
    }


    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if input states are not audit type in the filtered tx`(
        notarizationType: NotarizationType
    ) {
        val mockInputStateProof = mock<UtxoFilteredData.SizeOnly<StateRef>>()

        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            filteredTxContents = mapOf("inputStateRefs" to mockInputStateProof),
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message).contains(
                "Error while processing request from client"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if transaction verification fails`(notarizationType: NotarizationType) {
        fun throwVerify() {
            throw IllegalArgumentException("DUMMY ERROR")
        }
        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            txVerificationLogic = ::throwVerify,
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message)
                .contains("Error while processing request from client")
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with general error if unhandled exception occurred in uniqueness checker`(
        notarizationType: NotarizationType
    ) {
        createAndCallServer(
            mockErrorUniquenessClientService(
                UniquenessCheckErrorUnhandledExceptionImpl(
                    IllegalArgumentException::class.java.name,
                    "Unhandled error!"
                ),
                notarizationType
            ),
            notarizationType = notarizationType
        ) {
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
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin server should respond with error if notary identity invalid`(notarizationType: NotarizationType) {
        createAndCallServer(
            mockSuccessfulUniquenessClientService(notarizationType),
            // What party we pass in here does not matter, it just needs to be different from the notary server party
            filteredTxContents = mapOf("notaryName" to MemberX500Name.parse("C=GB,L=London,O=Bob")),
            notarizationType = notarizationType
        ) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseError).isInstanceOf(NotaryExceptionGeneral::class.java)
            assertThat((responseError as NotaryExceptionGeneral).message)
                .contains("Error while processing request from client")
        }
    }

    @Suppress("LongParameterList")
    private fun createAndCallServer(
        clientService: LedgerUniquenessCheckerClientService,
        notaryServiceKey: PublicKey = notaryServiceCompositeKey,
        filteredTxContents: Map<String, Any?> = emptyMap(),
        flowSession: FlowSession? = null,
        notarizationType: NotarizationType,
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

        // 2. Get current notary and parse its data
        whenever(mockMemberLookup.myInfo()).thenReturn(notaryInfo)
        whenever(notaryInfo.memberProvidedContext).thenReturn(memberProvidedContext)
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_NAME, MemberX500Name::class.java)).thenReturn(notaryServiceName)
        whenever(memberProvidedContext.parse(NOTARY_SERVICE_BACKCHAIN_REQUIRED, Boolean::class.java)).thenReturn(true)

        // 3. Check if any filtered transaction data should be overwritten
        val filteredTx = mock<UtxoFilteredTransaction> {
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

        // 4. Mock the receive and send from the counterparty session, unless it is overwritten
        val paramOrDefaultSession = flowSession ?: mock {
            on { receive(NonValidatingNotarizationPayload::class.java) } doReturn NonValidatingNotarizationPayload(
                filteredTx,
                notaryServiceKey,
                notarizationType
            )
            on { send(any()) } doAnswer {
                responseFromServer.add(it.arguments.first() as NotarizationResponse)
                Unit
            }
            on { counterparty } doReturn memberCharlieName
        }

        val server = NonValidatingNotaryServerFlowImpl(
            clientService,
            mockTransactionSignatureService,
            mockMemberLookup
        )

        server.call(paramOrDefaultSession)

        extractData(responseFromServer)
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
