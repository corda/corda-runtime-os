package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
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
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.membership.NotaryInfo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
        assertThat(createdBuilder, `is`(builder))

        // Using the same services we passed in
        assertThat(capture, `is`(Triple(signingService, persistenceService, config)))
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

        whenever(persistenceService.find(eq(UtxoTransactionEntity::class.java), eq(String(transaction.id.bytes))))
            .thenReturn(transaction.toEntity())
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
        assertThat(retrievedLedgerTx?.id, `is`(transaction.toLedgerTransaction().id))
        assertThat(retrievedSignedTx, `is`(transaction))
    }

    @Test
    fun `should be able to retrieve unconsumed states`(){
        // Given a persistence service in which we stored a transaction output entity
        val fiber = mock<SimFiber>()
        val serializationService = BaseSerializationService()
        val persistenceService = mock<PersistenceService>()
        val testState = TestUtxoState("StateData", publicKeys)
        val utxoTxOutputEntity = UtxoTransactionOutputEntity(
            "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D",
            TestUtxoState::class.java.canonicalName,
            serializationService.serialize(testState).bytes,
            2,
            false
        )
        val utxoOutputQueryResult = listOf(utxoTxOutputEntity)

        val utxoOutputQuery = mock<ParameterizedQuery<UtxoTransactionOutputEntity>>()
        whenever(persistenceService.query(eq("UtxoTransactionOutputEntity.findUnconsumedStatesByType"),
            eq(UtxoTransactionOutputEntity::class.java))).thenReturn(utxoOutputQuery)
        whenever(utxoOutputQuery.setParameter(eq("type"), any())).thenReturn(utxoOutputQuery)
        whenever(utxoOutputQuery.execute()).thenReturn(utxoOutputQueryResult)
        whenever(fiber.createMemberLookup(any())).thenReturn(mock())
        whenever(fiber.createSigningService(any())).thenReturn(mock())
        whenever(fiber.createNotarySigningService()).thenReturn(mock())
        whenever(fiber.getOrCreatePersistenceService(any())).thenReturn(persistenceService)

        val notaryInfo = mock<NotaryInfo>()
        whenever(fiber.getNotary()).thenReturn(notaryInfo)
        whenever(notaryInfo.name).thenReturn(notary.name)
        whenever(notaryInfo.publicKey).thenReturn(notary.owningKey)

        // When we retrieve the unconsumed state from the ledger service
        val utxoLedgerService = SimUtxoLedgerService(alice, fiber, config)
        val stateAndRefs = utxoLedgerService.findUnconsumedStatesByType(TestUtxoState::class.java)

        // Then it should be converted into StateAndRef
        assertThat(stateAndRefs.size, `is`(1))
        assertThat(stateAndRefs[0].state.contractState.name, `is`(testState.name))
        assertThat(stateAndRefs[0].state.contractState.participants, `is`(testState.participants))
        assertThat(stateAndRefs[0].state.notary, `is`(notary))
        assertThat(stateAndRefs[0].ref.transactionHash, `is`(SecureHash.parse(utxoTxOutputEntity.transactionId)))
        assertThat(stateAndRefs[0].ref.index, `is`(utxoTxOutputEntity.index))

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