package net.corda.ledger.libs.uniqueness.backingstore

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.impl.DefaultSqlQueryProvider
import net.corda.ledger.libs.uniqueness.backingstore.impl.SqlTransactionOpsImpl
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.orm.EntityManagerConfiguration
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.mock
import java.sql.Connection
import java.time.Instant
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionOpsDbIntegrationTest {
    private val dbConfig: EntityManagerConfiguration
    private val holdingIdentity = mock<UniquenessHoldingIdentity>()
    private val metricsFactory = mock<BackingStoreMetricsFactory>()

    init {
        // uncomment this to run the test against local Postgres
        System.setProperty("databaseType", "POSTGRES")

        dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_tx_ops")

        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/vnode-uniqueness/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
    }

    @Test
    fun createUnconsumedStatesTest() {
        val stateRefs = createStateRefs(3)

        dbConfig.dataSource.connection.use { connection ->
            connection.autoCommit = true
            val txOps = createTxOps(connection)
            txOps.createUnconsumedStates(stateRefs)
        }

        val foundIds = mutableListOf<ByteArray>()

        dbConfig.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val rs = statement.executeQuery(
                    """
                    SELECT 
                        issue_tx_id_algo,
                        issue_tx_id,
                        issue_tx_output_idx,
                        consuming_tx_id_algo,
                        consuming_tx_id
                    FROM uniqueness_state_details
                    """.trimIndent()
                )

                assertSoftly { softly ->
                    while (rs.next()) {
                        val txId = rs.getBytes("issue_tx_id")
                        stateRefs.singleOrNull { it.hash.bytes.contentEquals(txId) }?.let {
                            foundIds.add(txId)
                            softly.assertThat(rs.getString("issue_tx_id_algo")).isEqualTo(it.hash.algorithm)
                            softly.assertThat(rs.getInt("issue_tx_output_idx")).isEqualTo(it.index)
                            softly.assertThat(rs.getString("consuming_tx_id_algo")).isNull()
                            softly.assertThat(rs.getBytes("consuming_tx_id")).isNull()
                        }
                    }
                }
                assertThat(foundIds).hasSize(3)
            }
        }
    }

    @Test
    fun consumeStatesTest() {
        val stateRefs = createStateRefs(3)

        dbConfig.dataSource.connection.use { connection ->
            connection.autoCommit = true
            createUnConsumedStates(connection, stateRefs)
        }

        val consumingState = stateRefs.first()
        val consumedStates = stateRefs.drop(1)
        dbConfig.dataSource.connection.use { connection ->
            connection.autoCommit = true
            val txOps = createTxOps(connection)
            txOps.consumeStates(consumingState.txHash, consumedStates)
        }

        dbConfig.dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    SELECT 
                        issue_tx_id,
                        issue_tx_id_algo
                    FROM uniqueness_state_details
                    WHERE 
                        consuming_tx_id_algo = ? AND 
                        consuming_tx_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, consumingState.hash.algorithm)
                statement.setBytes(2, consumingState.hash.bytes)

                val rs = statement.executeQuery()
                val found = mutableListOf<SecureHash>()
                while (rs.next()) {
                    found.add(SecureHashImpl(rs.getString("issue_tx_id_algo"), rs.getBytes("issue_tx_id")))
                }
                @Suppress("SpreadOperator")
                assertThat(found)
                    .containsExactlyInAnyOrder(*consumedStates.map { it.hash }.toTypedArray())
            }
        }
    }

    @Test
    fun commitTransactionsTest() {
        val requests = dbConfig.dataSource.connection.use { connection ->
            connection.autoCommit = true
            createRequests(connection, 3)
        }

        val success = listOf(
            Pair(requests[0], UniquenessCheckResultSuccessImpl(Instant.now())),
            Pair(requests[1], UniquenessCheckResultSuccessImpl(Instant.now())),
        )
        val rejected = listOf(
            Pair(requests[2], UniquenessCheckResultFailureImpl(Instant.now(), object : UniquenessCheckError {})),
        )

        dbConfig.dataSource.connection.use { connection ->
            connection.autoCommit = true
            val txOps = createTxOps(connection)
            txOps.commitTransactions(success + rejected)
        }

        dbConfig.dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    SELECT 
                        tx_id_algo,
                        tx_id,
                        originator_x500_name,
                        expiry_datetime,
                        commit_timestamp,
                        result
                    FROM uniqueness_tx_details
                    WHERE 
                        originator_x500_name = ? OR originator_x500_name = ? OR originator_x500_name = ?
                    ORDER BY originator_x500_name
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, success.first().first.originatorX500Name)
                statement.setString(2, success.drop(1).first().first.originatorX500Name)
                statement.setString(3, rejected.first().first.originatorX500Name)

                val rs = statement.executeQuery()
                val compare = ArrayDeque((success + rejected).sortedBy { it.first.originatorX500Name })
                assertSoftly { softly ->
                    while (rs.next()) {
                        val expected = compare.removeFirst()
                        softly.assertThat(rs.getString("tx_id_algo")).isEqualTo(expected.first.txId.algorithm)
                        softly.assertThat(rs.getBytes("tx_id")).isEqualTo(expected.first.txId.bytes)
                        softly.assertThat(rs.getTimestamp("expiry_datetime").toInstant()).isEqualTo(expected.first.timeWindowUpperBound)
                        softly.assertThat(rs.getTimestamp("commit_timestamp").toInstant()).isEqualTo(expected.second.resultTimestamp)
                        softly.assertThat(rs.getString("result")).isEqualTo(expected.second.toCharacterRepresentation().toString())
                    }
                }
            }

            // rejected
            connection.prepareStatement(
                """
                    SELECT 
                        error_details
                    FROM uniqueness_rejected_txs
                    WHERE 
                        tx_id_algo = ? AND tx_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, rejected.first().first.txId.algorithm)
                statement.setBytes(2, rejected.first().first.txId.bytes)

                val rs = statement.executeQuery()
                assertThat(rs.next()).isTrue()
                assertThat(rs.getString("error_details")).isNotEmpty()
            }
        }
    }

    private fun createRequests(connection: Connection, n: Int): List<UniquenessCheckRequestInternal> {
        val inputStates = ArrayDeque(createStateRefs(n * 2))
        createUnConsumedStates(connection, inputStates)

        return (1..n).map {
            UniquenessCheckRequestInternal(
                SecureHashImpl("algo-$it", randomBytes()),
                "TX-${UUID.randomUUID()}-$it",
                "X500-$it-${UUID.randomUUID()}",
                listOf(inputStates.removeFirst()),
                listOf(inputStates.removeFirst()),
                1,
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(1000),
            )
        }
    }

    private fun createUnConsumedStates(connection: Connection, stateRefs: List<StateRef>) {
        connection
            .prepareStatement(
                """
                    INSERT INTO uniqueness_state_details(
                        issue_tx_id_algo, 
                        issue_tx_id, 
                        issue_tx_output_idx)
                    VALUES (?,?,?)
                """.trimIndent()
            )
            .use { statement ->
                stateRefs.forEach { stateRef ->
                    statement.setString(1, stateRef.txHash.algorithm)
                    statement.setBytes(2, stateRef.txHash.bytes)
                    statement.setInt(3, stateRef.stateIndex)

                    statement.addBatch()
                    println(statement)
                }
                statement.executeBatch()
            }
    }

    private fun createStateRefs(n: Int): List<StateRef> {
        return (1..n).map {
            StateRef(SecureHashImpl("algo-$it", randomBytes()), it)
        }
    }

    private fun createTxOps(connection: Connection): BackingStore.Session.TransactionOps {
        return SqlTransactionOpsImpl(
            connection,
            DefaultSqlQueryProvider(),
            object : UniquenessSecureHashFactory {
                override fun createSecureHash(algorithm: String, bytes: ByteArray): SecureHash {
                    return SecureHashImpl(algorithm, bytes)
                }

                override fun getBytes(hash: SecureHash): ByteArray {
                    return hash.bytes
                }

                override fun parseSecureHash(hashString: String): SecureHash {
                    hashString.split('|').let {
                        return SecureHashImpl(it[0], ByteArrays.parseAsHex(it[2]))
                    }
                }
            },
            metricsFactory,
            holdingIdentity,
        )
    }
}
