package net.corda.simulator.runtime.ledger

import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.time.Instant.now

class ConsensualSignedTransactionBaseTest {

    companion object {
        private val publicKeys = generateKeys(3)
        private val config = SimulatorConfigurationBuilder.create().build()

    }

    @Test
    fun `should be able to add signatories via the injected SigningService`() {
        val signingService = mock<SigningService>()

        val signatures = publicKeys.map {
            val signatureWithMetadata = toSignatureWithMetadata(it)
            whenever(signingService.sign(any(), eq(it), any())).thenReturn(signatureWithMetadata.signature)
            signatureWithMetadata
        }

        val ledgerInfo = ConsensualStateLedgerInfo(listOf(MyConsensualState(publicKeys)), now())
        val signedByOneTx = ConsensualSignedTransactionBase(
            listOf(signatures[0]),
            ledgerInfo,
            signingService,
            config
        )
        val signedByTwoTx = signedByOneTx.addSignature(publicKeys[1])
        val signedByAllTx = signedByTwoTx.addSignature(signatures[2])
        assertThat(signedByAllTx.signatures.map {it.by}, `is`(publicKeys))
    }

    @Test
    fun `should be able to provide the ledger transaction`() {
        val ledgerInfo = ConsensualStateLedgerInfo(listOf(MyConsensualState(publicKeys)), now())
        val tx = ConsensualSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it) },
            ledgerInfo,
            mock(),
            config
        )
        val ledgerTransaction = tx.toLedgerTransaction()
        assertThat(ledgerTransaction.id, `is`(tx.id))
        assertThat(ledgerTransaction.states, `is`(ledgerInfo.states))
        assertThat(ledgerTransaction.timestamp, `is`(ledgerInfo.timestamp))
        assertThat(ledgerTransaction.requiredSignatories, `is`(publicKeys.toSet()))
    }

    @Test
    fun `should not change the timestamp or id even when signed`() {
        // Given we control the clock
        val clock = mock<Clock>()
        whenever(clock.instant()).thenReturn(now())
        val config = SimulatorConfigurationBuilder.create().withClock(clock).build()

        // But we already gave the LedgerInfo a time
        val ledgerInfo = ConsensualStateLedgerInfo(listOf(MyConsensualState(publicKeys)), Instant.EPOCH)
        val signingService = mock<SigningService>()

        whenever(signingService.sign(
            any(),
            eq(publicKeys[0]),
            any())
        ).thenReturn(toSignatureWithMetadata(publicKeys[0]).signature)

        val unsignedTx = ConsensualSignedTransactionBase(
            listOf(),
            ledgerInfo,
            signingService,
            config
        )

        // When we sign it
        val signedTx = unsignedTx.addSignature(publicKeys[0])
        val ledgerTransaction = signedTx.toLedgerTransaction()

        // Then the id and timestamp should be unchanged
        assertThat(ledgerTransaction.id, `is`(unsignedTx.id))
        assertThat(ledgerTransaction.timestamp, `is`(ledgerInfo.timestamp))
    }

    private fun toSignatureWithMetadata(it: PublicKey) = DigitalSignatureAndMetadata(
        DigitalSignature.WithKey(it, "some bytes".toByteArray(), mapOf()),
        DigitalSignatureMetadata(now(), mapOf())
    )

    class MyConsensualState(override val participants: List<PublicKey>) : ConsensualState {
        override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    }
}