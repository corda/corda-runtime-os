package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.days
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

//TODO Run test with inputs
class UtxoSignedTransactionBaseTest {
    private val notaryX500 = MemberX500Name.parse("O=Notary,L=London,C=GB")
    private val config = SimulatorConfigurationBuilder.create().build()
    private val publicKeys = generateKeys(2)
    private val notary = Party(notaryX500, generateKey())

    @Test
    fun `should be able to provide ledger transaction`(){
        // Given a signed utxo transaction
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()

        val signedTx = UtxoSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it, Instant.now()) },
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
                listOf(TestUtxoState("State1", publicKeys), TestUtxoState("State2", publicKeys)),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        // If we try to retrieve the ledger transaction
        val ledgerTx = signedTx.toLedgerTransaction()

        // Then we should get the correct ledger transaction with the sam tx id
        assertThat(ledgerTx.id, `is`(signedTx.id))
    }

    @Test
    fun `should be equal to another transaction with the same data`(){
        // Given two different utxo ledger with same data
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()
        val timestamp = Instant.now()
        val timeWindow = SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis()))

        val ledgerInfo1 = UtxoStateLedgerInfo(
            listOf(TestUtxoCommand()),
            emptyList(),
            notary,
            emptyList(),
            publicKeys,
            timeWindow,
            listOf(TestUtxoState("State1", publicKeys), TestUtxoState("State2", publicKeys)),
            emptyList()
        )
        val ledgerInfo2 = UtxoStateLedgerInfo(
            listOf(TestUtxoCommand()),
            emptyList(),
            notary,
            emptyList(),
            publicKeys,
            timeWindow,
            listOf(TestUtxoState("State1", publicKeys), TestUtxoState("State2", publicKeys)),
            emptyList()
        )

        // When we build two different transaction with them
        val tx1 = UtxoSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it, timestamp) },
            ledgerInfo1,
            signingService,
            serializationService,
            persistenceService,
            config
        )

        val tx2 = UtxoSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it, timestamp) },
            ledgerInfo2,
            signingService,
            serializationService,
            persistenceService,
            config
        )

        // Then the two transaction should be considered as the same
        assertThat(tx1, `is`(tx2))
        assertThat(tx1.hashCode(), `is`(tx2.hashCode()))
    }

    @Test
    fun `should be able to convert to a JPA entity and back again`(){
        // Given a signed utxo  transaction
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()

        val tx = UtxoSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it, Instant.now()) },
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
                listOf(TestUtxoState("State1", publicKeys), TestUtxoState("State2", publicKeys)),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        // When we convert to the entity and back again
        val entity = tx.toEntity()
        val txFromEntity = UtxoSignedTransactionBase.fromEntity(
            entity, signingService, serializationService, persistenceService, config
        )

        // We should receive the transaction back
        assertThat(tx, `is`(txFromEntity))
        assertThat(tx.toLedgerTransaction(), `is`(txFromEntity.toLedgerTransaction()))
    }

    @Test
    fun `should be able to add signatories`(){
        // Geven a signed utxo transaction signed with one key
        val signingService = mock<SigningService>()
        val signatures = publicKeys.map {
            val signatureWithMetadata = toSignatureWithMetadata(it)
            whenever(signingService.sign(any(), eq(it), any())).thenReturn(signatureWithMetadata.signature)
            signatureWithMetadata
        }
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val txWithOneSignature = UtxoSignedTransactionBase(
            listOf(signatures[0]),
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
                listOf(TestUtxoState("State1", publicKeys), TestUtxoState("State2", publicKeys)),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        // When we add another signature to it
        whenever(signingService.findMySigningKeys(any())).thenReturn(mapOf(publicKeys[1] to publicKeys[1]))
        val txWithTwoSignatures = txWithOneSignature.addSignatures(listOf(publicKeys[1]))

        // The final transaction should be signed with both the keys
        assertThat(txWithTwoSignatures.signatures.map {it.by}, `is`(publicKeys))
    }

    private fun toSignatureWithMetadata(key: PublicKey, timestamp: Instant = Instant.now()) = DigitalSignatureAndMetadata(
        DigitalSignature.WithKey(key, "some bytes".toByteArray(), mapOf()),
        DigitalSignatureMetadata(timestamp, SignatureSpec("dummySignatureName"), mapOf())
    )

    class TestUtxoState(
        val name: String,
        override val participants: List<PublicKey>
    ) : ContractState

    class TestUtxoCommand: Command
}