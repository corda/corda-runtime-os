package com.r3.corda.notary.plugin.nonvalidating.client

import com.r3.corda.notary.plugin.common.NotarizationResponse
import com.r3.corda.notary.plugin.common.NotaryExceptionReferenceStateUnknown
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
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
class NonValidatingNotaryClientFlowImplTest {

    private companion object {
        /* Signature */
        val dummyUniquenessSignature = DigitalSignatureAndMetadata(
            mock(),
            DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
        )

        /* State Refs */
        val mockStateAndRef = mock<StateAndRef<*>> {
            on { ref } doReturn StateRef(SecureHashUtils.randomSecureHash(), 0)
        }

        /* Transactions */
        val mockFilteredTx = mock<UtxoFilteredTransaction>()

        val mockLedgerTransaction = mock<UtxoLedgerTransaction> {
            on { inputStateAndRefs } doReturn listOf(mockStateAndRef)
            on { outputStateAndRefs } doReturn emptyList()
        }

        val txId = SecureHashUtils.randomSecureHash()
        val mockUtxoTx = mock<UtxoSignedTransaction> {
            on { toLedgerTransaction() } doReturn mockLedgerTransaction
            on { id } doReturn txId
            on { notaryName } doReturn MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")
            on { notaryKey } doReturn mock<PublicKey>()
        }
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin client generates payload properly`(notarizationType: NotarizationType) {
        val client = createClient(notarizationType, mock())

        val payload = client.generatePayload(mockUtxoTx)

        assertAll({
                      assertThat(payload).isNotNull
                      assertThat(payload.transaction).isEqualTo(mockFilteredTx)
                  })
    }

    @ParameterizedTest
    @EnumSource(NotarizationType::class)
    fun `Non-validating notary plugin client returns signature on successful notarization`(notarizationType: NotarizationType) {
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
    fun `Non-validating notary plugin client throws error on failed notarization`(notarizationType: NotarizationType) {
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

    private fun createClient(notarizationType: NotarizationType, flowMessaging: FlowMessaging): NonValidatingNotaryClientFlowImpl {

        val mockBuilder = mock<UtxoFilteredTransactionBuilder> {
            on { withInputStates() } doReturn this.mock
            on { withReferenceStates() } doReturn this.mock
            on { withOutputStatesSize() } doReturn this.mock
            on { withNotary() } doReturn this.mock
            on { withTimeWindow() } doReturn this.mock
            on { build() } doReturn mockFilteredTx
        }

        return NonValidatingNotaryClientFlowImpl(
            mockUtxoTx,
            MemberX500Name("Alice", "Alice Corp", "LDN", "GB"),
            notarizationType,
            flowMessaging,
            mock {
                on { filterSignedTransaction(any()) } doReturn mockBuilder
            },
        )
    }
}
