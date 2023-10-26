package net.corda.ledger.utxo.flow.impl.transaction.signatureproof

import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class SignatureProofTests : UtxoLedgerTest() {
    companion object {
        private lateinit var signedTransaction: UtxoSignedTransactionInternal
        private val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
    }

    @BeforeEach
    fun beforeEach() {
        val notaryInfo = mock<NotaryInfo>().also {
            whenever(it.name).thenReturn(notaryX500Name)
            whenever(it.publicKey).thenReturn(publicKeyExample)
        }
        whenever(mockNotaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
        signedTransaction = UtxoTransactionBuilderImpl(
            utxoSignedTransactionFactory,
            mockNotaryLookup
        )
            .setNotary(notaryX500Name)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(anotherPublicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction() as UtxoSignedTransactionInternal
    }

    @Test
    fun `sign() produces a signature without proof`() {
        val signatures = transactionSignatureService.sign(signedTransaction, listOf(publicKeyExample))
        assertEquals(1, signatures.size)
        val signature: DigitalSignatureAndMetadata = signatures.first()
        assertNull(signature.proof)
    }

    @Test
    fun `signBatch() produces a signature with proof`() {
        val batchSignatures = transactionSignatureService.signBatch(listOf(signedTransaction), listOf(publicKeyExample))
        assertEquals(1, batchSignatures.size)
        val batch: List<DigitalSignatureAndMetadata> = batchSignatures.first()
        assertEquals(1, batch.size)
        val signature: DigitalSignatureAndMetadata = batch.first()
        assertNotNull(signature.proof)
    }
}
