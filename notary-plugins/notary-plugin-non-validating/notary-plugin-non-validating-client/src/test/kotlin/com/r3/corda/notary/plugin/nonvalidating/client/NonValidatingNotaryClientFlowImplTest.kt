package com.r3.corda.notary.plugin.nonvalidating.client

import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorInputStateConflict
import com.r3.corda.notary.plugin.common.NotaryErrorInputStateConflictImpl
import com.r3.corda.notary.plugin.common.NotaryException
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonValidatingNotaryClientFlowImplTest {

    private companion object {
        /* Members */
        const val DUMMY_PLATFORM_VERSION = 9001

        /* Signature */
        val mockRequestSignature = mock<DigitalSignature.WithKey>()
        val dummyUniquenessSignature = DigitalSignatureAndMetadata(
            mock(),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
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
            on { notary } doReturn Party(MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"), mock())
        }
    }

    @Test
    fun `Non-validating notary plugin client generates payload properly`() {
        val client = createClient(mock())

        val payload = client.generatePayload(mockUtxoTx)

        assertAll({
            assertThat(payload).isNotNull
            assertThat(payload.transaction).isEqualTo(mockFilteredTx)
            assertThat(payload.requestSignature.digitalSignature).isEqualTo(mockRequestSignature)
            assertThat(payload.requestSignature.platformVersion).isEqualTo(DUMMY_PLATFORM_VERSION)
        })
    }

    @Test
    fun `Non-validating notary plugin client returns signature on successful notarisation`() {
        val mockSession = mock<FlowSession> {
            on { sendAndReceive(eq(NotarisationResponse::class.java), any()) } doReturn NotarisationResponse(
                listOf(dummyUniquenessSignature),
                null
            )
        }
        val mockFlowMessaging = mock<FlowMessaging> {
            on { initiateFlow(any()) } doReturn mockSession
        }

        val client = createClient(mockFlowMessaging)

        val signatures = client.call()

        assertThat(signatures).containsExactly(dummyUniquenessSignature)
    }

    @Test
    fun `Non-validating notary plugin client throws error on failed notarisation`() {
        val mockSession = mock<FlowSession> {
            on { sendAndReceive(eq(NotarisationResponse::class.java), any()) } doReturn NotarisationResponse(
                emptyList(),
                NotaryErrorInputStateConflictImpl(emptyList())
            )
        }
        val mockFlowMessaging = mock<FlowMessaging> {
            on { initiateFlow(any()) } doReturn mockSession
        }

        val client = createClient(mockFlowMessaging)

        val ex = assertThrows<NotaryException> {
            client.call()
        }

        assertThat(ex.error).isInstanceOf(NotaryErrorInputStateConflict::class.java)
        assertThat(ex.txId).isEqualTo(txId)
    }

    private fun createClient(flowMessaging: FlowMessaging): NonValidatingNotaryClientFlowImpl {
        val mockMemberInfo = mock<MemberInfo> {
            on { platformVersion } doReturn DUMMY_PLATFORM_VERSION
            on { sessionInitiationKey } doReturn mock()
        }

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
            Party(MemberX500Name("Alice", "Alice Corp", "LDN", "GB"), mock()),
            flowMessaging,
            mock {
                on { myInfo() } doReturn mockMemberInfo
            },
            mock {
                on { serialize(any()) } doReturn SerializedBytes("ABC".toByteArray())
            },
            mock {
                on { sign(any(), any(), any()) } doReturn mockRequestSignature
            },
            mock {
                on { filterSignedTransaction(any()) } doReturn mockBuilder
            }
        )
    }
}
