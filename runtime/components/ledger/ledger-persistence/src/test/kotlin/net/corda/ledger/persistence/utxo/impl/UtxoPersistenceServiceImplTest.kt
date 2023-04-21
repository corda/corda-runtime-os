package net.corda.ledger.persistence.utxo.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.json.impl.ContractStateVaultJsonFactoryImpl
import net.corda.ledger.persistence.json.impl.ContractStateVaultJsonFactoryRegistryImpl
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

// FIXME / NOTE: This test only tests custom representation (JSON) string functionality
class UtxoPersistenceServiceImplTest {

    private val persistedJsonStrings = mutableMapOf<String, CustomRepresentation>()

    private val mockRepository = mock<UtxoRepository> {
        on { persistTransactionVisibleStates(any(), any(), any(), any(), any(), any(), any()) } doAnswer {
            val txId = it.getArgument<String>(1)
            val customRepresentation = it.getArgument<CustomRepresentation>(5)
            persistedJsonStrings[txId] = customRepresentation
        }

        on { persistTransaction(any(), any(), any(), any(), any()) } doAnswer {}
        on { persistTransactionComponentLeaf(any(), any(), any(), any(), any(), any(), any()) } doAnswer {}
        on { persistTransactionOutput(any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any()) } doAnswer {}
    }

    private val mockPrivacySalt = mock<PrivacySalt> {
        on { bytes } doReturn ByteArray(0)
    }

    @Suppress("unchecked_cast")
    private val storage = ContractStateVaultJsonFactoryRegistryImpl().apply {
        registerJsonFactory(ContractStateVaultJsonFactoryImpl())
        registerJsonFactory(DummyStateJsonFactory() as ContractStateVaultJsonFactory<ContractState>)
        registerJsonFactory(InvalidStateJsonFactory() as ContractStateVaultJsonFactory<ContractState>)
    }

    private val mockEm = mock<EntityManager> {
        on { transaction } doReturn mock()
    }

    private val mockEmFactory = mock<EntityManagerFactory> {
        on { createEntityManager() }.doReturn(mockEm)
    }

    private val persistenceService = UtxoPersistenceServiceImpl(
        mockEmFactory,
        mockRepository,
        mock(),
        mock(),
        storage,
        JsonMarshallingServiceImpl(), // We could mock this but this is basically just a layer on top of Jackson
        UTCClock()
    )

    @BeforeEach
    fun clearCache() {
        persistedJsonStrings.clear()
    }

    @Test
    fun `Persisting a transaction while JSON parsing fails will result in an empty JSON string being stored`() {
        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(InvalidState())
        ))

        persistenceService.persistTransaction(tx)

        assertThat(persistedJsonStrings).hasSize(1)

        val persisted = persistedJsonStrings.entries.first()

        assertJsonContentEquals(
            expected = """
                {
                    "net.corda.ledger.persistence.utxo.impl.InvalidState" : {
                    
                    },
                    "net.corda.v5.ledger.utxo.ContractState" : {
                    
                    }
                }
            """.trimIndent(),
            actual = persisted.value.json
        )
    }

    @Test
    fun `Persisting a transaction with an empty string JSON factory will result in storing {}`() {
        val storage = ContractStateVaultJsonFactoryRegistryImpl().apply {
            registerJsonFactory(EmptyStateJsonFactory()) // Register the factory that returns empty string
        }

        val singlePersistenceService = UtxoPersistenceServiceImpl(
            mockEmFactory,
            mockRepository,
            mock(),
            mock(),
            storage,
            JsonMarshallingServiceImpl(),
            UTCClock()
        )

        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(EmptyState())
        ))

        singlePersistenceService.persistTransaction(tx)

        assertThat(persistedJsonStrings).hasSize(1)

        val persisted = persistedJsonStrings.entries.first()

        assertJsonContentEquals(
            expected = """
                {
                    "net.corda.ledger.persistence.utxo.impl.EmptyState" : {
                    
                    }
                }
            """.trimIndent(),
            actual = persisted.value.json
        )
    }

    @Test
    fun `Persisting a transaction with multiple JSON factories will result in a combined JSON string being stored`() {
        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(DummyState("DUMMY"))
        ))

        persistenceService.persistTransaction(tx)

        assertThat(persistedJsonStrings).hasSize(1)
        val persisted = persistedJsonStrings.entries.first()

        assertJsonContentEquals(
            expected = """
            {
              "net.corda.v5.ledger.utxo.ContractState" : {
              },
              "net.corda.ledger.persistence.utxo.impl.DummyState" : {
                "dummyField" : "DUMMY",
                "dummyField2" : "DUMMY"
              }
            }
            """.trimIndent(),
            actual = persisted.value.json
        )
    }

    @Test
    fun `Persisting a transaction while no JSON factory is present for the given type will result in using the ContractState factory`() {
        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(ContractState { emptyList() }) // State that has no specific factory
        ))

        persistenceService.persistTransaction(tx)

        assertThat(persistedJsonStrings).hasSize(1)
        val persisted = persistedJsonStrings.entries.first()

        assertJsonContentEquals(
            expected = """
            {
                "net.corda.v5.ledger.utxo.ContractState" : {
                }
            }
            """.trimIndent(),
            actual = persisted.value.json
        )
    }

    @Test
    fun `Persisting a transaction while zero JSON factory is registered will result in an empty JSON string being stored`() {
        val emptyPersistenceService = UtxoPersistenceServiceImpl(
            mockEmFactory,
            mockRepository,
            mock(),
            mock(),
            ContractStateVaultJsonFactoryRegistryImpl(), // Empty storage
            JsonMarshallingServiceImpl(),
            UTCClock()
        )

        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(ContractState { emptyList() })
        ))

        emptyPersistenceService.persistTransaction(tx)

        assertThat(persistedJsonStrings).hasSize(1)
        val persisted = persistedJsonStrings.entries.first()

        assertThat(persisted.value.json).isEqualTo("{}")
    }

    private fun createMockTransaction(producedStates: Map<Int, StateAndRef<ContractState>>): UtxoTransactionReader {
        return mock {
            on { getConsumedStateRefs() } doReturn emptyList()
            on { rawGroupLists } doReturn emptyList()
            on { visibleStatesIndexes } doReturn listOf(0)
            on { status } doReturn TransactionStatus.UNVERIFIED
            on { signatures } doReturn emptyList()
            on { id } doReturn randomSecureHash()
            on { privacySalt } doReturn mockPrivacySalt
            on { account } doReturn ""
            on { getVisibleStates() } doReturn producedStates
        }
    }

    private inline fun <reified T : ContractState> createStateAndRef(returnState: T): StateAndRef<T> {
        val txState = mock<TransactionState<T>> {
            on { contractState } doReturn returnState
        }
        return mock {
            on { state } doReturn txState
        }
    }

    private fun assertJsonContentEquals(
        expected: String,
        actual: String
    ) {
        val om = ObjectMapper()

        assertEquals(
            om.readTree(expected),
            om.readTree(actual)
        )
    }
}

private data class DummyState(val dummyField: String) : ContractState {
    override fun getParticipants(): MutableList<PublicKey> = mutableListOf()
}

private class DummyStateJsonFactory : ContractStateVaultJsonFactory<DummyState> {
    override fun getStateType(): Class<DummyState> = DummyState::class.java
    override fun create(state: DummyState, jsonMarshallingService: JsonMarshallingService): String {
        return """
            {
                "dummyField": "${state.dummyField}",
                "dummyField2": "${state.dummyField}"
            }
            """.trimIndent()
    }
}

private class InvalidState : ContractState {
    override fun getParticipants(): MutableList<PublicKey> = mutableListOf()
}

private class InvalidStateJsonFactory : ContractStateVaultJsonFactory<InvalidState> {
    override fun getStateType(): Class<InvalidState> = InvalidState::class.java
    override fun create(state: InvalidState, jsonMarshallingService: JsonMarshallingService): String {
        return "INVALID"
    }
}

private class EmptyState : ContractState {
    override fun getParticipants(): MutableList<PublicKey> = mutableListOf()
}

private class EmptyStateJsonFactory : ContractStateVaultJsonFactory<EmptyState> {
    override fun getStateType(): Class<EmptyState> = EmptyState::class.java
    override fun create(state: EmptyState, jsonMarshallingService: JsonMarshallingService): String {
        return ""
    }
}
