package com.r3.corda.notary.plugin.contractverifying.client

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionReferenceStateUnknown
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractVerifyingNotaryClientFlowImplTest {

    private companion object {
        val txId = SecureHashUtils.randomSecureHash()
        val hashedNotaryKey = SecureHashUtils.randomSecureHash()
    }

    /* State Refs */
    private val mockStateRef = mock<StateRef> {
        on { transactionId } doReturn txId
    }

    /* notary public keys */
    private val encodedNotary = byteArrayOf(0x01)
    private val notaryPublicKey = mock<PublicKey> {
        on { encoded } doReturn encodedNotary
    }

    /* Transactions */
    private val mockFilteredTx = mock<UtxoFilteredTransaction>()
    private val mockSignedTx = mock<UtxoSignedTransaction> {
        on { notaryKey } doReturn notaryPublicKey
        on { inputStateRefs } doReturn listOf(mockStateRef)
        on { referenceStateRefs } doReturn emptyList()
    }

    /* Signature */
    private val dummyUniquenessSignature = DigitalSignatureAndMetadata(
        mock(),
        DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
    )
    private val mockDependency = mock<UtxoSignedTransaction> {
        on { signatures } doReturn listOf(dummyUniquenessSignature)
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin client creates payload properly`(notarizationType: NotarizationType) {
        val client = createClient(notarizationType, mock())

        val payload = client.createPayload()

        assertAll({
                      assertThat(payload).isNotNull
                      assertThat(payload.initialTransaction).isEqualTo(mockSignedTx)
                  })
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin client returns signature on successful notarization`(notarizationType: NotarizationType) {
        val mockSession = mock<FlowSession> {
            on { sendAndReceive(eq(NotarizationResponse::class.java), any()) } doReturn NotarizationResponse(
                listOf(dummyUniquenessSignature),
                null
            )
        }
        val mockFlowMessaging = mock<FlowMessaging> {
            on { initiateFlow(any()) } doReturn mockSession
        }

        val client = createClient(notarizationType, mockFlowMessaging)

        val signatures = client.call()

        assertThat(signatures).containsExactly(dummyUniquenessSignature)
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Contract verifying notary plugin client throws error on failed notarization`(notarizationType: NotarizationType) {
        val mockSession = mock<FlowSession> {
            on { sendAndReceive(eq(NotarizationResponse::class.java), any()) } doReturn NotarizationResponse(
                emptyList(),
                NotaryExceptionReferenceStateUnknown(emptyList(), txId)
            )
        }
        val mockFlowMessaging = mock<FlowMessaging> {
            on { initiateFlow(any()) } doReturn mockSession
        }

        val client = createClient(notarizationType, mockFlowMessaging)

        val ex = assertThrows<NotaryExceptionReferenceStateUnknown> {
            client.call()
        }

        assertThat(ex.txId).isEqualTo(txId)
    }

    private fun createClient(notarizationType: NotarizationType, flowMessaging: FlowMessaging): ContractVerifyingNotaryClientFlowImpl {

        val mockBuilder = mock<UtxoFilteredTransactionBuilder> {
            on { withOutputStates(listOf(0)) } doReturn this.mock
            on { withNotary() } doReturn this.mock
            on { withTimeWindow() } doReturn this.mock
            on { build() } doReturn mockFilteredTx
        }

        val utxoLedgerService = mock<UtxoLedgerService> {
            on { filterSignedTransaction(any()) } doReturn mockBuilder
            on { findSignedTransaction(any()) } doReturn mockDependency
        }

        val digestService = mock<DigestService> {
            on { hash(mockSignedTx.notaryKey.encoded, DigestAlgorithmName.SHA2_256) } doReturn hashedNotaryKey
        }

        return ContractVerifyingNotaryClientFlowImpl(
            mockSignedTx,
            MemberX500Name("Alice", "Alice Corp", "LDN", "GB"),
            notarizationType,
            flowMessaging,
            utxoLedgerService,
            digestService
        )
    }
}
