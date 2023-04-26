package net.corda.simulator.runtime.ledger.utxo

import net.corda.crypto.core.bytes
import net.corda.crypto.core.parseSecureHash
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.notary.BaseNotaryInfo
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PagedQuery.ResultSet
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.membership.NotaryInfo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.time.Duration.Companion.days

class SimUtxoLedgerServiceTest {

    private val alice = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val notaryX500 = MemberX500Name.parse("O=Notary,L=London,C=GB")
    private val config = SimulatorConfigurationBuilder.create().build()
    private val publicKeys = generateKeys(2)
    private val notaryKey = generateKey()
    private val fiber = mock<SimFiber>()
    private val persistenceService = mock<PersistenceService>()
    private val signingService = mock<SigningService>()
    private val notaryLookup = mock<NotaryLookup>()

    @BeforeEach
    fun `setup ledgerService`(){
        whenever(fiber.createSigningService(any())).thenReturn(signingService)
        whenever(fiber.getOrCreatePersistenceService(any())).thenReturn(persistenceService)
        whenever(fiber.createNotaryLookup()).thenReturn(notaryLookup)
        whenever(fiber.createMemberLookup(alice)).thenReturn(mock())
        whenever(fiber.createNotarySigningService()).thenReturn(mock())
        whenever(fiber.getNotary()).thenReturn(BaseNotaryInfo(notaryX500, "", emptySet(), notaryKey))
    }

    @Test
    fun `should be able to build a UtxoTransactionBuilder using the given factory`() {
        // Given a factory for building UTBs that and something to capture what we build it with
        val builder = mock<UtxoTransactionBuilder>()

        lateinit var capture : Quad<SigningService, PersistenceService, SimulatorConfiguration, NotaryLookup>
        val builderFactory = UtxoTransactionBuilderFactory { ss, per, c, nl->
            capture = Quad(ss, per, c, nl)
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
        val createdBuilder = ledgerService.createTransactionBuilder()

        // Then it should be created by the factory
        assertThat(createdBuilder, `is`(builder))

        // Using the same services we passed in
        assertThat(capture, `is`(Quad(signingService, persistenceService, config, notaryLookup)))
    }

    @Test
    fun `should be able to retrieve stored transaction`(){
        // Given a persistence service in which we stored a transaction entity
        val serializationService = BaseSerializationService()
        val transaction = UtxoSignedTransactionBase(
            publicKeys.map { toSignatureWithMetadata(it) },
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.inWholeMilliseconds)),
                listOf(ContractStateAndEncumbranceTag(TestUtxoState("StateData", publicKeys), null)),
                emptyList(),
                notaryX500,
                notaryKey
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        whenever(persistenceService.find(eq(UtxoTransactionEntity::class.java), eq(String(transaction.id.bytes))))
            .thenReturn(transaction.toEntity())

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
        val serializationService = BaseSerializationService()
        val testState = TestUtxoState("StateData", publicKeys)
        val utxoTxOutputEntity = UtxoTransactionOutputEntity(
            "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D",
            TestUtxoState::class.java.canonicalName,
            serializationService.serialize(listOf(SimEncumbranceGroup(1, "tag"))).bytes,
            serializationService.serialize(testState).bytes,
            2,
            false
        )

        val utxoOutputQueryResult = object : ResultSet<UtxoTransactionOutputEntity> {
            override fun getResults(): List<UtxoTransactionOutputEntity> {
                return next()
            }
            override fun hasNext(): Boolean {
                throw IllegalStateException("Unused")
            }
            override fun next(): List<UtxoTransactionOutputEntity> {
                return listOf(utxoTxOutputEntity)
            }
        }

        val utxoOutputQuery = mock<ParameterizedQuery<UtxoTransactionOutputEntity>>()
        whenever(persistenceService.query(eq("UtxoTransactionOutputEntity.findUnconsumedStatesByType"),
            eq(UtxoTransactionOutputEntity::class.java))).thenReturn(utxoOutputQuery)
        whenever(utxoOutputQuery.setParameter(eq("type"), any())).thenReturn(utxoOutputQuery)
        whenever(utxoOutputQuery.execute()).thenReturn(utxoOutputQueryResult)

        val notaryInfo = mock<NotaryInfo>()
        whenever(fiber.getNotary()).thenReturn(notaryInfo)
        whenever(notaryInfo.name).thenReturn(notaryX500)
        whenever(notaryInfo.publicKey).thenReturn(notaryKey)

        // When we retrieve the unconsumed state from the ledger service
        val utxoLedgerService = SimUtxoLedgerService(alice, fiber, config)
        val stateAndRefs = utxoLedgerService.findUnconsumedStatesByType(TestUtxoState::class.java)

        // Then it should be converted into StateAndRef
        assertThat(stateAndRefs.size, `is`(1))
        assertThat(stateAndRefs[0].state.contractState, `is`(testState))
        assertThat(stateAndRefs[0].state.notaryName, `is`(notaryX500))
        assertThat(stateAndRefs[0].ref.transactionId, `is`(parseSecureHash(utxoTxOutputEntity.transactionId)))
        assertThat(stateAndRefs[0].ref.index, `is`(utxoTxOutputEntity.index))

    }

    @Test
    fun `should be able to resolve stateRef to stateAndRef`(){
        val serializationService = BaseSerializationService()
        whenever(fiber.getNotary()).thenReturn(BaseNotaryInfo(notaryX500, "", emptySet(), notaryKey))
        val entityId = UtxoTransactionOutputEntityId(
            "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D",
            0
        )
        val testState = TestUtxoState("TestState", publicKeys)
        val encumbrance = SimEncumbranceGroup(1, "tag")
        val utxoOutputEntity = UtxoTransactionOutputEntity(
            "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D",
            TestUtxoState::class.java.canonicalName,
            serializationService.serialize(listOf(encumbrance)).bytes,
            serializationService.serialize(testState).bytes,
            0,
            false
        )
        whenever(persistenceService.find(
            eq(UtxoTransactionOutputEntity::class.java),
            eq(entityId))).thenReturn(utxoOutputEntity)
        val stateRef = StateRef(
            parseSecureHash("SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D"),
            0
        )
        val utxoLedgerService = SimUtxoLedgerService(alice, fiber, config)
        val returnedStateAndRef = utxoLedgerService.resolve<TestUtxoState>(stateRef)

        assertThat(returnedStateAndRef.ref, `is`(stateRef))
        assertThat(returnedStateAndRef.state.contractState, `is`(testState))
        assertThat(returnedStateAndRef.state.notaryName, `is`(notaryX500))
        assertThat(returnedStateAndRef.state.encumbranceGroup, `is`(encumbrance))
    }
}