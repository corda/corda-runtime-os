package net.corda.ledger.persistence.utxo.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.json.DefaultContractStateVaultJsonFactory
import net.corda.ledger.persistence.json.impl.DefaultContractStateVaultJsonFactoryImpl
import net.corda.ledger.persistence.json.impl.ContractStateVaultJsonFactoryRegistryImpl
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.IllegalArgumentException
import java.security.PublicKey
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

// FIXME / NOTE: This test only tests custom representation (JSON) string functionality
class UtxoPersistenceServiceImplTest {

    private val persistedJsonStrings = mutableMapOf<String, CustomRepresentation>()

    private val mockRepository = mock<UtxoRepository> {
        on { persistVisibleTransactionOutput(
            any(), any(), any(), any(), any(), any(), any(), any(),
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
        ) } doAnswer {
            val txId = it.getArgument<String>(1)
            val customRepresentation = it.arguments.single {
                it as? CustomRepresentation != null
            } as CustomRepresentation
            persistedJsonStrings[txId] = customRepresentation
        }

        on { persistTransaction(any(), any(), any(), any(), any(), any(), any()) } doAnswer {}
        on { persistTransactionComponentLeaf(any(), any(), any(), any(), any(), any()) } doAnswer {}
    }
    private val mockDigestService = mock<DigestService> {
        on { hash(any<ByteArray>(), any())} doAnswer { SecureHashImpl("algo", byteArrayOf(1, 2, 11)) }
    }

    private val mockPrivacySalt = mock<PrivacySalt> {
        on { bytes } doReturn ByteArray(0)
    }

    @Suppress("unchecked_cast")
    private val storage = ContractStateVaultJsonFactoryRegistryImpl().apply {
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
        mockDigestService,
        storage,
        DefaultContractStateVaultJsonFactoryImpl(),
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
                        "stateRef": "hash:0"
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
        val emptyDefaultContractStateVaultJsonFactory = mock<DefaultContractStateVaultJsonFactory>()
        whenever(emptyDefaultContractStateVaultJsonFactory.create(any(), any())).thenReturn("")

        val singlePersistenceService = UtxoPersistenceServiceImpl(
            mockEmFactory,
            mockRepository,
            mock(),
            mockDigestService,
            storage,
            emptyDefaultContractStateVaultJsonFactory,
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
                "stateRef": "hash:0"
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
    fun `Persisting a transaction while no JSON factory is present for the given type will store the default state json`() {
        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(NoJsonFactoryState()) // State that has no specific factory
        ))

        persistenceService.persistTransaction(tx)

        assertThat(persistedJsonStrings).hasSize(1)
        val persisted = persistedJsonStrings.entries.first()

        assertJsonContentEquals(
            expected = """
            {
                "net.corda.v5.ledger.utxo.ContractState" : {
                    "stateRef": "hash:0"
                }
            }
            """.trimIndent(),
            actual = persisted.value.json
        )
    }

    @Test
    fun `Persisting a transaction while zero JSON factories are registered will result still store the default state json`() {
        val emptyPersistenceService = UtxoPersistenceServiceImpl(
            mockEmFactory,
            mockRepository,
            mock(),
            mockDigestService,
            ContractStateVaultJsonFactoryRegistryImpl(), // Empty storage
            DefaultContractStateVaultJsonFactoryImpl(),
            JsonMarshallingServiceImpl(),
            UTCClock()
        )

        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(NoJsonFactoryState())
        ))

        emptyPersistenceService.persistTransaction(tx)

        assertThat(persistedJsonStrings).hasSize(1)
        val persisted = persistedJsonStrings.entries.first()

        assertJsonContentEquals(
            expected = """
            {
                "net.corda.v5.ledger.utxo.ContractState" : {
                    "stateRef": "hash:0"
                }
            }
            """.trimIndent(),
            actual = persisted.value.json
        )
    }

    @Test
    fun `if an exception is thrown in a json factory, the state should still be persisted and that field should be {}`() {

        val storage = ContractStateVaultJsonFactoryRegistryImpl().apply {
            registerJsonFactory(ExceptionStateFactory()) // Register the factory that throws an exception
        }

        val persistenceService = UtxoPersistenceServiceImpl(
            mockEmFactory,
            mockRepository,
            mock(),
            mockDigestService,
            storage,
            DefaultContractStateVaultJsonFactoryImpl(),
            JsonMarshallingServiceImpl(),
            UTCClock()
        )

        val tx = createMockTransaction(mapOf(
            0 to createStateAndRef(ExceptionState("a", "b", "c"))
        ))

        assertDoesNotThrow {
            persistenceService.persistTransaction(tx)
        }

        assertThat(persistedJsonStrings).hasSize(1)
        val persisted = persistedJsonStrings.entries.first()

        assertJsonContentEquals(
            expected = """
            {
                "net.corda.ledger.persistence.utxo.impl.ExceptionState" : {
                },
                "net.corda.v5.ledger.utxo.ContractState" : {
                    "stateRef": "hash:0"
                }
            }
            """.trimIndent(),
            actual = persisted.value.json
        )
    }
    private fun createMockTransaction(producedStates: Map<Int, StateAndRef<ContractState>>): UtxoTransactionReader {
        return mock {
            on { getConsumedStateRefs() } doReturn emptyList()
            on { rawGroupLists } doReturn listOf(listOf("{}".toByteArray()))
            on { visibleStatesIndexes } doReturn listOf(0)
            on { status } doReturn TransactionStatus.UNVERIFIED
            on { signatures } doReturn emptyList()
            on { id } doReturn randomSecureHash()
            on { privacySalt } doReturn mockPrivacySalt
            on { account } doReturn ""
            on { getVisibleStates() } doReturn producedStates
            on { metadata } doReturn TransactionMetadataImpl(
                mapOf(
                    "membershipGroupParametersHash" to "membershipGroupParametersHash",
                    "cpiMetadata" to mapOf(
                        "name" to "name",
                        "version" to "version",
                        "signerSummaryHash" to "signerSummaryHash",
                        "fileChecksum" to "cpiFileChecksum"
                    )
                )
            )
        }
    }

    /**
     * Due to introduction of hidden classes in Java 15,
     * getCanonicalName() returns null, which is accessed via
     * PersistenceService.persistenceTransaction(tx) using getCanonicalName().
     * getCanonicalName() being null indicates that the hidden or anonymous class has no canonical name and
     * mock objects created by Mockito are implemented using anonymous inner classes in Java
     * hence this fake contractState implemented to avoid class being anonymous.
     */
    private class MockTransactionState<T : ContractState>(
        private val ctrState: T
    ) : TransactionState<T> {
        override fun getContractState(): T {
            return ctrState
        }

        override fun getContractStateType(): Class<T> {
            TODO("Not yet implemented")
        }

        override fun getContractType(): Class<out Contract> {
            TODO("Not yet implemented")
        }

        override fun getNotaryName(): MemberX500Name {
            return MemberX500Name.parse("O=notary, L=London, C=GB")
        }

        override fun getNotaryKey(): PublicKey {
            TODO("Not yet implemented")
        }

        override fun getEncumbranceGroup(): EncumbranceGroup? {
            TODO("Not yet implemented")
        }

    }

    private inline fun <reified T : ContractState> createStateAndRef(returnState: T): StateAndRef<T> {
        val txState = MockTransactionState(returnState)
        val secureHash = mock<SecureHash> {
            on { toString() } doReturn "hash"
        }
        return mock {
            on { state } doReturn txState
            on { ref } doReturn StateRef(secureHash, 0)
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

private class NoJsonFactoryState : ContractState {

    override fun getParticipants(): MutableList<PublicKey> = mutableListOf()
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

private class ExceptionState(
    val dummyString: String,
    val dummyString2: String,
    val dummyString3: String
) : ContractState {
    override fun getParticipants(): MutableList<PublicKey> = mutableListOf()
}

private class ExceptionStateFactory : ContractStateVaultJsonFactory<ExceptionState> {
    override fun getStateType(): Class<ExceptionState> = ExceptionState::class.java

    override fun create(state: ExceptionState, jsonMarshallingService: JsonMarshallingService): String {
        throw IllegalArgumentException("Creation error!")
    }
}
