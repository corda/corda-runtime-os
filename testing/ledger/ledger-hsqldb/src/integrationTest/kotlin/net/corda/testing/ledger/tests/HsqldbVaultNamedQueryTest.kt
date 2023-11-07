package net.corda.testing.ledger.tests

import com.example.ledger.testing.datamodel.utxo.UtxoTransactionComponentEntity
import com.example.ledger.testing.datamodel.utxo.UtxoTransactionComponentEntityId
import com.example.ledger.testing.datamodel.utxo.UtxoTransactionEntity
import com.example.ledger.testing.datamodel.utxo.UtxoTransactionMetadataEntity
import com.example.ledger.testing.datamodel.utxo.UtxoVisibleTransactionOutputEntity
import com.example.ledger.testing.datamodel.utxo.UtxoVisibleTransactionOutputEntityId
import com.example.ledger.testing.datamodel.utxo.UtxoTransactionSignatureEntity
import com.example.ledger.testing.datamodel.utxo.UtxoTransactionSignatureEntityId
import com.example.ledger.testing.datamodel.utxo.UtxoTransactionSourceEntity
import com.example.ledger.testing.datamodel.utxo.UtxoTransactionSourceEntityId
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory
import javax.persistence.Query
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.hsqldb.json.HsqldbJsonExtension.JSON_SQL_TYPE
import net.corda.db.persistence.testkit.components.DataSourceAdmin
import net.corda.db.schema.DbSchema
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParser
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.utils.transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@TestInstance(PER_CLASS)
@ExtendWith(ServiceExtension::class)
class HsqldbVaultNamedQueryTest {
    private companion object {
        private const val UTXO_VISIBLE_TX_STATE = "utxo_visible_transaction_output"
        private const val BASE_QUERY = "SELECT * FROM $UTXO_VISIBLE_TX_STATE AS visible_state WHERE "
        private const val TIMEOUT_MILLIS = 10000L

        private val ACCOUNT_ID = UUID.randomUUID()
    }

    private class StateData(val groupIndex: Int, val leafIndex: Int, val customRepresentation: String)

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var vaultNamedQueryParser: VaultNamedQueryParser

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var dbConnectionManager: DbConnectionManager

    private lateinit var entityManagerFactory: EntityManagerFactory

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        dataSourceAdmin: DataSourceAdmin,
        @InjectService(timeout = TIMEOUT_MILLIS)
        liquibaseSchemaMigrator: LiquibaseSchemaMigrator
    ) {
        val dbConnectionId = UUID.randomUUID()

        // migrate DB schema
        val vaultSchema = ClassloaderChangeLog(linkedSetOf(
            ClassloaderChangeLog.ChangeLogResourceFiles(
                DbSchema::class.java.packageName,
                listOf("net/corda/db/schema/vnode-vault/db.changelog-master.xml"),
                DbSchema::class.java.classLoader
            )
        ))

        val dataSource = dataSourceAdmin.getOrCreateDataSource(dbConnectionId, "connection-0")
        dataSource.connection.use { connection ->
            liquibaseSchemaMigrator.updateDb(connection, vaultSchema)
        }

        val entities = JpaEntitiesSet.create("utxo-ledger", setOf(
            UtxoTransactionEntity::class.java,
            UtxoTransactionMetadataEntity::class.java,
            UtxoTransactionComponentEntity::class.java,
            UtxoTransactionComponentEntityId::class.java,
            UtxoVisibleTransactionOutputEntity::class.java,
            UtxoVisibleTransactionOutputEntityId::class.java,
            UtxoTransactionSignatureEntity::class.java,
            UtxoTransactionSignatureEntityId::class.java,
            UtxoTransactionSourceEntity::class.java,
            UtxoTransactionSourceEntityId::class.java
        ))
        entityManagerFactory = dbConnectionManager.createEntityManagerFactory(dbConnectionId, entities)
    }

    private fun persistVisibleStates(txId: UUID, states: Iterable<StateData>) {
        entityManagerFactory.transaction { em ->
            val timestamp = Instant.now()

            val metadata = em.find(UtxoTransactionMetadataEntity::class.java, "hash") ?: UtxoTransactionMetadataEntity(
                "hash",
                "canonicalData".toByteArray(),
                "groupParametersHash",
                "cpiFileChecksum"
            ).also{
                em.persist(it)
            }
            val tx = UtxoTransactionEntity(
                id = txId.toString(),
                privacySalt = byteArrayOf(),
                accountId = ACCOUNT_ID.toString(),
                created = timestamp,
                status = TransactionStatus.VERIFIED.value,
                updated = timestamp,
                metadata = metadata
            )

            val visibleStates = mutableListOf<UtxoVisibleTransactionOutputEntity>()
            for (state in states) {
                tx.components += UtxoTransactionComponentEntity(
                    transaction = tx,
                    groupIndex = state.groupIndex,
                    leafIndex = state.leafIndex,
                    data = byteArrayOf(0x01, 0x02, 0x03, 0x04),
                    hash = ""
                )

                visibleStates += UtxoVisibleTransactionOutputEntity(
                    transaction = tx,
                    groupIndex = state.groupIndex,
                    leafIndex = state.leafIndex,
                    type = "com.r3.Dummy",
                    tokenType = null,
                    tokenIssuerHash = null,
                    tokenNotaryX500Name = null,
                    tokenSymbol = null,
                    tokenTag = null,
                    tokenOwnerHash = null,
                    tokenAmount = null,
                    customRepresentation = state.customRepresentation,
                    created = timestamp,
                    consumed = null
                )
            }

            em.persist(tx)
            visibleStates.forEach(em::persist)
        }
    }

    private fun executeQuery(sqlText: String, txId: UUID, parameters: (Query) -> Unit): List<UtxoVisibleTransactionOutputEntity> {
        @Suppress("unchecked_cast")
        return entityManagerFactory.transaction { em ->
            em.createNativeQuery("$BASE_QUERY $sqlText AND transaction_id = :txId", UtxoVisibleTransactionOutputEntity::class.java)
                .setParameter("txId", txId.toString())
                .also(parameters)
                .resultList
        } as List<UtxoVisibleTransactionOutputEntity>
    }

    @Test
    fun testJsonField() {
        val txId = UUID.randomUUID()
        persistVisibleStates(txId, listOf(
            StateData(0, 0, "{ \"a\": { \"x\": -50 } }"),
            StateData(10, 20, "{ \"a\": { \"b\": \"Hello World!\" } }"),
            StateData(30, 50, "{ \"a\": { \"b\": 999 }, \"e\": 200 }")
        ))

        val sqlText = vaultNamedQueryParser.parseWhereJson("WHERE (visible_state.custom_representation)->(a)->>(b) = :value")
        assertThat(sqlText)
            .isEqualTo("JsonFieldAsText( JsonFieldAsObject( CAST(visible_state.custom_representation AS $JSON_SQL_TYPE), 'a'), 'b') = :value")

        val numberResult = executeQuery(sqlText, txId) { query ->
            query.setParameter("value", 999)
        }.single()
        assertAll(
            { assertEquals(txId.toString(), numberResult.transaction.id) },
            { assertEquals(30, numberResult.groupIndex) },
            { assertEquals(50, numberResult.leafIndex) }
        )

        val stringResult = executeQuery(sqlText, txId) { query ->
            query.setParameter("value", "Hello World!")
        }.single()
        assertAll(
            { assertEquals(txId.toString(), stringResult.transaction.id) },
            { assertEquals(10, stringResult.groupIndex) },
            { assertEquals(20, stringResult.leafIndex) }
        )
    }

    @Test
    fun testJsonArrayField() {
        val txId = UUID.randomUUID()
        persistVisibleStates(txId, listOf(
            StateData(0, 1, "{ \"a\": [ \"fee\", \"fie\", \"foe\", \"foo\" ] }"),
            StateData(15, 25, "{ \"a\": [ 10, 20, 30, -45 ] }"),
            StateData(200, 250, "{ \"a\": [], \"e\": 200 }")
        ))

        val sqlText = vaultNamedQueryParser.parseWhereJson("WHERE (visible_state.custom_representation)->(a)->>(:index::int) = :value")
        assertThat(sqlText)
            .isEqualTo("JsonFieldAsText( JsonFieldAsObject( CAST(visible_state.custom_representation AS $JSON_SQL_TYPE), 'a'), ( CAST(:index AS int))) = :value")

        val numberResult = executeQuery(sqlText, txId) { query ->
            query.setParameter("index", 3)
            query.setParameter("value", -45)
        }.single()
        assertAll(
            { assertEquals(txId.toString(), numberResult.transaction.id) },
            { assertEquals(15, numberResult.groupIndex) },
            { assertEquals(25, numberResult.leafIndex) }
        )

        val stringResult = executeQuery(sqlText, txId) { query ->
            query.setParameter("index", 2)
            query.setParameter("value", "foe")
        }.single()
        assertAll(
            { assertEquals(txId.toString(), stringResult.transaction.id) },
            { assertEquals(0, stringResult.groupIndex) },
            { assertEquals(1, stringResult.leafIndex) }
        )
    }

    @Test
    fun testJsonArrayObject() {
        val txId = UUID.randomUUID()
        persistVisibleStates(txId, listOf(
            StateData(10, 11, "[ { \"a\": \"Fee!\" }, { \"b\": \"Fie!\" }, { \"c\": \"Foe!\" }, { \"d\": \"Foo!\" } ]"),
            StateData(25, 35, "[ { \"a\": 10 }, { \"b\": 20 }, { \"c\": 300 } ]"),
            StateData(300, 350, "[]")
        ))

        val sqlText = vaultNamedQueryParser.parseWhereJson("WHERE (visible_state.custom_representation)->(:index::int)->>(c) = :value")
        assertThat(sqlText)
            .isEqualTo("JsonFieldAsText( JsonFieldAsObject( CAST(visible_state.custom_representation AS $JSON_SQL_TYPE), ( CAST(:index AS int))), 'c') = :value")

        val numberResult = executeQuery(sqlText, txId) { query ->
            query.setParameter("index", 2)
            query.setParameter("value", 300)
        }.single()
        assertAll(
            { assertEquals(txId.toString(), numberResult.transaction.id) },
            { assertEquals(25, numberResult.groupIndex) },
            { assertEquals(35, numberResult.leafIndex) }
        )

        val stringResult = executeQuery(sqlText, txId) { query ->
            query.setParameter("index", 2)
            query.setParameter("value", "Foe!")
        }.single()
        assertAll(
            { assertEquals(txId.toString(), stringResult.transaction.id) },
            { assertEquals(10, stringResult.groupIndex) },
            { assertEquals(11, stringResult.leafIndex) }
        )
    }

    @Test
    fun testHasJsonKeyForObject() {
        val txId = UUID.randomUUID()
        persistVisibleStates(txId, listOf(
            StateData(0, 0, "{ \"a\": { \"b\": -50 } }"),
            StateData(10, 20, "{ \"c\": { \"d\": 10 }, \"f\": { \"a\": \"Hello World\" } }"),
            StateData(30, 50, "{ \"a\": { \"d\": 999 }, \"e\": 200 }")
        ))

        val sqlText = vaultNamedQueryParser.parseWhereJson("WHERE (visible_state.custom_representation)->(a)?(d)")
        assertThat(sqlText)
            .isEqualTo("HasJsonKey( JsonFieldAsObject( CAST(visible_state.custom_representation AS $JSON_SQL_TYPE), 'a'), 'd')")

        val visibleState = executeQuery(sqlText, txId) {}.single()
        assertAll(
            { assertEquals(txId.toString(), visibleState.transaction.id) },
            { assertEquals(30, visibleState.groupIndex) },
            { assertEquals(50, visibleState.leafIndex) }
        )
    }

    @Test
    fun testJsonCast() {
        val txId = UUID.randomUUID()
        persistVisibleStates(txId, listOf(
            StateData(0, 0, "{ \"a\": { \"b\": -50 } }"),
            StateData(10, 20, "{ \"a\": { \"b\": 10 } }"),
            StateData(30, 50, "{ \"a\": { \"b\": 999 } }")
        ))

        val sqlText = vaultNamedQueryParser.parseWhereJson("WHERE (visible_state.custom_representation)->(a)->>(b)::int = :value")
        assertThat(sqlText)
            .isEqualTo("CAST( JsonFieldAsText( JsonFieldAsObject( CAST(visible_state.custom_representation AS $JSON_SQL_TYPE), 'a'), 'b') AS int) = :value")

        val visibleState = executeQuery(sqlText, txId) { query ->
            query.setParameter("value", 10)
        }.single()

        assertAll(
            { assertEquals(txId.toString(), visibleState.transaction.id) },
            { assertEquals(10, visibleState.groupIndex) },
            { assertEquals(20, visibleState.leafIndex) }
        )
    }
}
