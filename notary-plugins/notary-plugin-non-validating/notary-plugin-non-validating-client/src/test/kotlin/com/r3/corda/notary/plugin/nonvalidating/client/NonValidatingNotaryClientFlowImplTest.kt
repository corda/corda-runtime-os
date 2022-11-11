package com.r3.corda.notary.plugin.nonvalidating.client

import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorInputStateConflict
import com.r3.corda.notary.plugin.common.NotaryErrorInputStateConflictImpl
import com.r3.corda.notary.plugin.common.NotaryException
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant

class NonValidatingNotaryClientFlowImplTest {

    private companion object {
        /* Members */
        const val DUMMY_PLATFORM_VERSION = 9001
        val dummyX500Name = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")

        val mockMemberInfo = mock<MemberInfo> {
            on { platformVersion } doReturn DUMMY_PLATFORM_VERSION
            on { sessionInitiationKey } doReturn mock()
        }

        val dummyParty = Party(dummyX500Name, mock())

        /* Signature */
        val mockRequestSignature = mock<DigitalSignature.WithKey>()
        val dummyUniquenessSignature = DigitalSignatureAndMetadata(
            mock(),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )

        /* State Refs */
        val dummyStateRef = StateRef(SecureHashUtils.randomSecureHash(), 0)
        val mockStateAndRef = mock<StateAndRef<*>> {
            on { ref } doReturn dummyStateRef
        }

        /* Transactions */
        val mockLedgerTransaction = mock<UtxoLedgerTransaction> {
            on { inputStateAndRefs } doReturn listOf(mockStateAndRef)
            on { outputStateAndRefs } doReturn emptyList()
        }

        val txId = SecureHashUtils.randomSecureHash()

        val mockUtxoTx = mock<UtxoSignedTransaction> {
            on { toLedgerTransaction() } doReturn mockLedgerTransaction
            on { id } doReturn txId
        }

        /* Responses */
        val successResponse = NotarisationResponse(listOf(dummyUniquenessSignature), null)
        val errorResponse = NotarisationResponse(emptyList(), NotaryErrorInputStateConflictImpl(emptyList()))

        /* Services */
        val mockSigningService = mock<SigningService> {
            on { sign(any(), any(), any()) } doReturn mockRequestSignature
        }

        val mockSerializationService = mock<SerializationService> {
            on { serialize(any()) } doReturn SerializedBytes("ABC".toByteArray())
        }

        val mockMemberLookupService = mock<MemberLookup> {
            on { myInfo() } doReturn mockMemberInfo
        }
    }

    @Test
    fun `Non-validating notary plugin client generates payload properly`() {
        val client = NonValidatingNotaryClientFlowImpl(
            mockUtxoTx,
            dummyParty
        ).also {
            it.flowMessaging = mock()
            it.memberLookupService = mockMemberLookupService
            it.serializationService = mockSerializationService
            it.signingService = mockSigningService
        }

        val payload = client.generatePayload(mockUtxoTx)

        assertThat(payload).isNotNull
        assertThat(payload.numOutputs).isEqualTo(0)
        assertThat(payload.transaction).isEqualTo(mockUtxoTx)
        assertThat(payload.requestSignature.digitalSignature).isEqualTo(mockRequestSignature)
        assertThat(payload.requestSignature.platformVersion).isEqualTo(DUMMY_PLATFORM_VERSION)
    }

    @Test
    fun `Non-validating notary plugin client returns signature on successful notarisation`() {
        val mockSession = mock<FlowSession> {
            on { sendAndReceive(eq(NotarisationResponse::class.java), any()) } doReturn successResponse
        }
        val mockFlowMessaging = mock<FlowMessaging> {
            on { initiateFlow(any()) } doReturn mockSession
        }

        val client = NonValidatingNotaryClientFlowImpl(
            mockUtxoTx,
            dummyParty
        ).also {
            it.flowMessaging = mockFlowMessaging
            it.memberLookupService = mockMemberLookupService
            it.serializationService = mockSerializationService
            it.signingService = mockSigningService
        }

        val signatures = client.call()

        assertThat(signatures).isNotEmpty
        assertThat(signatures).isNotNull
        assertThat(signatures).containsExactly(dummyUniquenessSignature)
    }

    @Test
    fun `Non-validating notary plugin client throws error on failed notarisation`() {
        val mockSession = mock<FlowSession> {
            on { sendAndReceive(eq(NotarisationResponse::class.java), any()) } doReturn errorResponse
        }
        val mockFlowMessaging = mock<FlowMessaging> {
            on { initiateFlow(any()) } doReturn mockSession
        }

        val client = NonValidatingNotaryClientFlowImpl(
            mockUtxoTx,
            dummyParty
        ).also {
            it.flowMessaging = mockFlowMessaging
            it.memberLookupService = mockMemberLookupService
            it.serializationService = mockSerializationService
            it.signingService = mockSigningService
        }

        val ex = assertThrows<NotaryException> {
            client.call()
        }

        assertThat(ex.error).isInstanceOf(NotaryErrorInputStateConflict::class.java)
        assertThat(ex.txId).isEqualTo(txId)
    }
}