package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.days
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

class SimUtxoLedgerServiceTest {

    private val alice = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val notaryX500 = MemberX500Name.parse("O=Notary,L=London,C=GB")
    private val config = SimulatorConfigurationBuilder.create().build()
    private val publicKeys = generateKeys(2)
    private val notary = Party(notaryX500, generateKey())

    @Test
    fun `should be able to build a UtxoTransactionBuilder using the given factory`() {
        // Given a factory for building UTBs that and something to capture what we build it with
        val builder = mock<UtxoTransactionBuilder>()
        val fiber = mock<SimFiber>()
        val signingService = mock<SigningService>()
        val persistenceService = mock<PersistenceService>()
        whenever(fiber.createSigningService(any())).thenReturn(signingService)
        whenever(fiber.getOrCreatePersistenceService(any())).thenReturn(persistenceService)
        whenever(fiber.createMemberLookup(any())).thenReturn(mock())
        whenever(fiber.createNotarySigningService()).thenReturn(mock())

        lateinit var capture : Triple<SigningService, PersistenceService, SimulatorConfiguration>
        val builderFactory = UtxoTransactionBuilderFactory { ss, per, c->
            capture = Triple(ss, per, c)
            builder
        }

        // And a utxo ledger service that will build transactions using it
        val ledgerService = SimUtxoLedgerService(
            alice,
            fiber,
            config,
            builderFactory
        )

        // When we get a builder
        val createdBuilder = ledgerService.getTransactionBuilder()

        // Then it should be created by the factory
        MatcherAssert.assertThat(createdBuilder, Matchers.`is`(builder))

        // Using the same services we passed in
        MatcherAssert.assertThat(
            capture,
            Matchers.`is`(Triple(signingService, persistenceService, config))
        )
    }

    @Test
    fun `should be able to retrieve stored transaction`(){
        // Given a persistence service in which we stored a transaction entity
        val fiber = mock<SimFiber>()
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()
        val transaction = UtxoSignedTransactionBase(
            publicKeys.map { toSignature(it) },
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
                listOf(TestUtxoState("StateData", publicKeys)),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        val utxoTxQuery = mock<ParameterizedQuery<UtxoTransactionEntity>>()
        whenever(persistenceService.query(eq("UtxoTransactionEntity.findByTransactionId"),
            eq(UtxoTransactionEntity::class.java))).thenReturn(utxoTxQuery)
        whenever(utxoTxQuery.setParameter(eq("transactionId"), any())).thenReturn(utxoTxQuery)
        whenever(utxoTxQuery.execute()).thenReturn(listOf(transaction.toEntity()))
        whenever(fiber.createMemberLookup(alice)).thenReturn(mock())
        whenever(fiber.createNotarySigningService()).thenReturn(mock())
        whenever(fiber.createSigningService(alice)).thenReturn(signingService)
        whenever(fiber.getOrCreatePersistenceService(alice)).thenReturn(persistenceService)

        // When we retrieve it or the ledger transaction from the ledger service
        val utxoLedgerService = SimUtxoLedgerService(alice, fiber, config)
        val retrievedSignedTx = utxoLedgerService.findSignedTransaction(transaction.id)
            ?: fail("No transaction retrieved")
        val retrievedLedgerTx = utxoLedgerService.findLedgerTransaction(transaction.id)

        // Then it should be converted successfully back into a transaction again
        MatcherAssert.assertThat(retrievedLedgerTx?.id, Matchers.`is`(transaction.toLedgerTransaction().id))
        MatcherAssert.assertThat(retrievedSignedTx, Matchers.`is`(transaction))
    }

    private fun toSignature(key: PublicKey) = DigitalSignatureAndMetadata(
        DigitalSignature.WithKey(key, "some bytes".toByteArray(), mapOf()),
        DigitalSignatureMetadata(Instant.now(), SignatureSpec("dummySignatureName"), mapOf())
    )

    class TestUtxoState(
        val name: String,
        override val participants: List<PublicKey>
    ) : ContractState

    class TestUtxoCommand: Command
}