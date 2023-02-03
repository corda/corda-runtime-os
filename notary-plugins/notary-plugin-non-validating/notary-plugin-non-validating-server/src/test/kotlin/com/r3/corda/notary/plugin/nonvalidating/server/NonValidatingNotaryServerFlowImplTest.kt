package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.NotarisationRequestSignature
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneral
import com.r3.corda.notary.plugin.common.NotaryErrorReferenceStateUnknown
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.ledger.common.Party
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
import java.security.PublicKey
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonValidatingNotaryServerFlowImplTest {

    private companion object {
        const val DUMMY_PLATFORM_VERSION = 9001

        /* Cache for storing response from server */
        val responseFromServer = mutableListOf<NotarisationResponse>()

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
            on { verify(any(), any(), any<ByteArray>(), any()) } doAnswer { }
        }
    }

    @BeforeEach
    fun clearCache() {
        responseFromServer.clear()
    }

    @Test
    fun `Non-validating notary should respond with error if the specified key that is not part of the notary composite key`() {
        // We sign with a key that is not part of the notary composite key
        createAndCallServer(mockSuccessfulUniquenessClientService(), currentVNodeNotaryKey = mock()) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText)
                .contains("Error while processing request from client")
            assertThat(responseError.cause).hasStackTraceContaining(
                "The notary key selected for signing is not associated with the notary service key."
            )
        }
    }

    @Test
    fun `Non-validating notary should respond with error if no keys found for signing`() {
        // We sign with a key that is not part of the notary composite key
        createAndCallServer(mockSuccessfulUniquenessClientService(), currentVNodeNotaryKey = null) {
            assertThat(responseFromServer).hasSize(1)

            val responseError = responseFromServer.first().error
            assertThat(responseError).isNotNull
            assertThat(responseFromServer.first().signatures).isEmpty()
            assertThat(responseError).isInstanceOf(NotaryErrorGeneral::class.java)
            assertThat((responseError as NotaryErrorGeneral).errorText)
                .contains("Error while processing request from client")
            assertThat(responseError.cause).hasStackTraceContaining(
                "Could not find any keys associated with the notary service's public key."
            )
        }
    }

    @Test
    fun `Non-validating notary should respond with success if the notary key is simple`() {
        // We sign with a key that is not part of the notary composite key
        createAndCallServer(mockSuccessfulUniquenessClientService(),
            currentVNodeNotaryKey = notaryVNodeAliceKey, notaryServiceKey = notaryVNodeAliceKey) {
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
            on { verify(any(), any(), any<ByteArray>(), any()) } doThrow IllegalArgumentException("Sig error")
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
            assertThat(response.signatures.first().by).isEqualTo(notaryVNodeAliceKey)
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

        createAndCallServer(
            mockSuccessfulUniquenessClientService(),
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
        currentVNodeNotaryKey: PublicKey? = notaryVNodeAliceKey,
        notaryServiceKey: PublicKey = notaryServiceCompositeKey,
        filteredTxContents: Map<String, Any?> = emptyMap(),
        sigVerifier: DigitalSignatureVerificationService = mockSigVerifier,
        flowSession: FlowSession? = null,
        txVerificationLogic: () -> Unit = {},
        extractData: (sigs: List<NotarisationResponse>) -> Unit
    ) {
        val txId = randomSecureHash()

        // 1. Set up the defaults for the filtered transaction
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
            on { receive(NonValidatingNotarisationPayload::class.java) } doReturn NonValidatingNotarisationPayload(
                filteredTx,
                NotarisationRequestSignature(
                    DigitalSignature.WithKey(
                        memberCharlieKey,
                        "ABC".toByteArray(),
                        emptyMap()
                    ),
                    DUMMY_PLATFORM_VERSION
                ),
                notaryServiceKey
            )
            on { send(any()) } doAnswer {
                responseFromServer.add(it.arguments.first() as NotarisationResponse)
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
            on { serialize(any()) } doReturn SerializedBytes("ABC".toByteArray())
        }

        // 6. Mock the required things for the batch signing
        val mockMerkleTree = mock<MerkleTree> {
            on { root } doReturn txId
        }
        val mockMerkleTreeFactory = mock<MerkleTreeFactory> {
            on { createHashDigest(any(), any()) } doReturn mock()
            on { createTree(any(), any()) } doReturn mockMerkleTree
        }

        val dummySignature = DigitalSignature.WithKey(
            // If the provided key is null, this will not be returned anyway so we just mock it
            currentVNodeNotaryKey ?: mock(),
            "some bytes".toByteArray(),
            mapOf()
        )

        val mockSigningService = mock<SigningService> {
            on { sign(any(), any(), any()) } doReturn dummySignature
            on { findMySigningKeys(any()) } doReturn mapOf(mock<PublicKey>() to currentVNodeNotaryKey)
        }

        val mockDigestService = mock<DigestService> {
            on { hash(any<ByteArray>(), any()) } doReturn randomSecureHash()
        }

        val server = NonValidatingNotaryServerFlowImpl(
            clientService,
            mockSerializationService,
            sigVerifier,
            mockMemberLookup,
            mockMerkleTreeFactory,
            mockSigningService,
            mockDigestService
        )

        server.call(paramOrDefaultSession)

        extractData(responseFromServer)
    }

    private fun mockSuccessfulUniquenessClientService(): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(UniquenessCheckResultSuccessImpl(Instant.now()))
    }

    private fun mockErrorUniquenessClientService(): LedgerUniquenessCheckerClientService {
        return mockUniquenessClientService(
            UniquenessCheckResultFailureImpl(
                Instant.now(),
                UniquenessCheckErrorReferenceStateUnknownImpl(emptyList())
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