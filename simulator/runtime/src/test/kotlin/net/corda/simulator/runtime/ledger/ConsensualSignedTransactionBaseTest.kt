package net.corda.simulator.runtime.ledger

import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
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
        val signedByAllTx = signedByTwoTx.addSignatures(listOf(signatures[2]))
        assertThat(signedByAllTx.signatures.map { it.by }, `is`(publicKeys))
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

        whenever(
            signingService.sign(
                any(),
                eq(publicKeys[0]),
                any()
            )
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

    @Test
    fun `should be able to convert to a JPA entity and back again`() {
        val ledgerInfo = ConsensualStateLedgerInfo(listOf(MyConsensualState(publicKeys)), now())
        val signingService = mock<SigningService>()
        val tx = ConsensualSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it) },
            ledgerInfo,
            signingService,
            config
        )
        val entity = tx.toEntity()
        val retrievedTx = ConsensualSignedTransactionBase.fromEntity(
            entity,
            signingService,
            BaseSerializationService(),
            config
        )

        assertThat(retrievedTx.signatures, `is`(tx.signatures))
        assertThat(retrievedTx.toLedgerTransaction(), `is`(tx.toLedgerTransaction()))
    }

    @Test
    fun `should be equal to another transaction with the same states and signatures`() {
        val timestamp = now()
        val ledgerInfo1 = ConsensualStateLedgerInfo(listOf(MyConsensualState(publicKeys)), timestamp)
        val tx1 = ConsensualSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it, timestamp) },
            ledgerInfo1,
            mock(),
            config
        )

        val ledgerInfo2 = ConsensualStateLedgerInfo(listOf(MyConsensualState(publicKeys)), timestamp)
        val tx2 = ConsensualSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it, timestamp) },
            ledgerInfo2,
            mock(),
            config
        )

        assertThat(tx1, `is`(tx2))
        assertThat(tx1.hashCode(), `is`(tx2.hashCode()))
    }

    private fun toSignatureWithMetadata(key: PublicKey, timestamp: Instant = now()) = DigitalSignatureAndMetadata(
        DigitalSignature.WithKey(key, "some bytes".toByteArray()),
        DigitalSignatureMetadata(timestamp, SignatureSpec("dummySignatureName"), mapOf())
    )

    @CordaSerializable
    data class MyConsensualState(private val participants: List<PublicKey>) : ConsensualState {

        override fun getParticipants(): List<PublicKey> {
            return participants
        }

        override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    }
}