package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.days
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
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

    fun `should be able to retrieve stored transaction`(){
        val fiber = mock<SimFiber>()
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()
        val transaction = UtxoSignedTransactionBase(
            listOf(TestUtxoCommand()),
            listOf(),
            notary,
            listOf(),
            publicKeys,
            listOf(),
            SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
            listOf(TestUtxoState("Test", publicKeys)),
            listOf(),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        val utxoTxQuery = mock<ParameterizedQuery<UtxoTransactionEntity>>()
        whenever(persistenceService.query(eq("UtxoTransactionEntity.findByTransactionId"),
            eq(UtxoTransactionEntity::class.java))).thenReturn(utxoTxQuery)
        whenever(utxoTxQuery.setParameter(eq("transactionId"), any())).thenReturn(utxoTxQuery)
        whenever(utxoTxQuery.execute()).thenReturn(listOf( transaction.toEntity()))
        whenever(fiber.createMemberLookup(alice)).thenReturn(mock())
        whenever(fiber.createNotarySigningService()).thenReturn(mock())
        whenever(fiber.createSigningService(alice)).thenReturn(signingService)
        whenever(fiber.getOrCreatePersistenceService(alice)).thenReturn(persistenceService)

        val utxoLedgerService = SimUtxoLedgerService(alice, fiber, config)
        val retrievedSignedTx = utxoLedgerService.findSignedTransaction(transaction.id)
            ?: fail("No transaction retrieved")
        val retrievedLedgerTx = utxoLedgerService.findLedgerTransaction(transaction.id)

        MatcherAssert.assertThat(retrievedLedgerTx, Matchers.`is`(transaction.toLedgerTransaction()))
        MatcherAssert.assertThat(retrievedSignedTx, Matchers.`is`(transaction))
        MatcherAssert.assertThat(retrievedSignedTx.id, Matchers.`is`(transaction.id))
    }

    fun `should be able to retrieve unconsumed states`(){
        val fiber = mock<SimFiber>()
        val utxoLedgerService = SimUtxoLedgerService(alice, fiber, config)
        val persistenceService = mock<PersistenceService>()
        val utxoOutputQuery = mock<ParameterizedQuery<UtxoTransactionOutputEntity>>()
        val utxoTxQuery = mock<ParameterizedQuery<UtxoTransactionEntity>>()
        val utxoTxOutputEntity = UtxoTransactionOutputEntity(
            "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D",
            TestUtxoState::class.java.canonicalName,
            0,
            false
        )

        val utxoOutputQueryResult = listOf(utxoTxOutputEntity)
        //val utxoTxQueryResult = listOf(utxoTxEntity)

        whenever(persistenceService.query(eq("UtxoTransactionOutputEntity.findUnconsumedStatesByType"),
            eq(UtxoTransactionOutputEntity::class.java))).thenReturn(utxoOutputQuery)
        whenever(utxoOutputQuery.execute()).thenReturn(utxoOutputQueryResult)
        whenever(persistenceService.query(eq("UtxoTransactionEntity.findByTransactionId"),
            eq(UtxoTransactionEntity::class.java))).thenReturn(utxoTxQuery)
        //whenever(utxoTxQuery.execute()).thenReturn(utxoTxQueryResult)

        utxoLedgerService.findUnconsumedStatesByType(TestUtxoState::class.java)

    }

    class TestUtxoState(
        val name: String,
        override val participants: List<PublicKey>
    ) : ContractState

    class TestUtxoCommand: Command
}