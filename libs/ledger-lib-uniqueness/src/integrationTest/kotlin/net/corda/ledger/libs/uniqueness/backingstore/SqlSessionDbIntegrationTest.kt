package net.corda.ledger.libs.uniqueness.backingstore

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.impl.SqlSessionImpl
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.mock
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlSessionDbIntegrationTest {
    companion object {
        val tzUTC: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    private val dbConfig: EntityManagerConfiguration

    init {
        // uncomment this to run the test against local Postgres
        System.setProperty("databaseType", "POSTGRES")

        dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_session")

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

    private val holdingIdentity = mock<UniquenessHoldingIdentity>()
    private val metricsFactory = mock<BackingStoreMetricsFactory>()
    private val exceptionCategorizer = mock<PersistenceExceptionCategorizer>()

    @Test
    fun getTransactionDetailsTest() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        val retrieveTxDetails = createTxDetails(3).take(2)
        dbConfig.dataSource.connection.use { connection ->
            val session = createSession(connection)
            val found = session.getTransactionDetails(retrieveTxDetails.map { SecureHashImpl(it.txIdAlgo, it.txId) })

            assertSoftly { softly ->
                softly.assertThat(found.count()).isEqualTo(retrieveTxDetails.count())
                retrieveTxDetails.forEach {
                    softly.assertThat(found[SecureHashImpl(it.txIdAlgo, it.txId)]?.result?.resultTimestamp)
                        .isEqualTo(it.commitTimestamp)
                }
            }
        }
    }

    @Test
    fun getStateDetailsTest() {
        Assumptions.assumeFalse(DbUtils.isInMemory, "Skipping this test when run against in-memory DB.")

        val retrieveDetails = createStateDetails(3).take(2)
        dbConfig.dataSource.connection.use { connection ->
            val session = createSession(connection)
            val found = session.getStateDetails(retrieveDetails.map { it.stateRef })

            assertSoftly { softly ->
                softly.assertThat(found.count()).isEqualTo(retrieveDetails.count())
                retrieveDetails.forEach {
                    val row = found.entries.single { r -> r.key.txHash == it.stateRef.txHash && r.key.stateIndex == r.key.stateIndex }.value
                    softly.assertThat(row.consumingTxId)
                        .isEqualTo(it.consumingTxId)
                }
            }
        }
    }

    private fun createSession(connection: Connection): BackingStore.Session {
        return SqlSessionImpl(
            holdingIdentity,
            connection,
            metricsFactory,
            exceptionCategorizer,
            object: UniquenessSecureHashFactory {
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
            }
        )
    }

    private fun createStateDetails(n: Int): List<UniquenessCheckStateDetails> {
        dbConfig.dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO uniqueness_state_details(issue_tx_id_algo, issue_tx_id, issue_tx_output_idx, consuming_tx_id_algo, consuming_tx_id)
                    VALUES (?,?,?,?,?)
                """.trimIndent()
                )
                .use { statement ->
                    val results = (1..n).map {
                        val details = StateDetails(
                            StateRef(SecureHashImpl("algo-$it", randomBytes()), it),
                            SecureHashImpl("c-algo-$it", randomBytes())
                        )


                        statement.setString(1, details.stateRef.txHash.algorithm)
                        statement.setBytes(2, details.stateRef.txHash.bytes)
                        statement.setInt(3, details.stateRef.stateIndex)
                        statement.setString(4, details.consumingId!!.algorithm)
                        statement.setBytes(5, details.consumingId.bytes)

                        statement.addBatch()
                        println(statement)
                        details
                    }


                    assertThat(statement.executeBatch().sum()).isEqualTo(n)
                    connection.commit()

                    return results
                }
        }
    }

    private fun createTxDetails(n: Int): List<TransactionDetails> {
        dbConfig.dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO
                        uniqueness_tx_details(tx_id_algo, tx_id, originator_x500_name, commit_timestamp, expiry_datetime, result)
                    VALUES (?,?,?,?,?,?)
                """.trimIndent()
                )
                .use { statement ->
                    val results = (1..n).map {
                        val txDetails = TransactionDetails(
                            "algo$it",
                            randomBytes(),
                            "X500 name$it",
                            Instant.now(),
                            'A',
                        )


                        statement.setString(1, txDetails.txIdAlgo)
                        statement.setBytes(2, txDetails.txId)
                        statement.setString(3, txDetails.originatorX500Name)
                        statement.setTimestamp(4,
                            Timestamp.from(txDetails.commitTimestamp), tzUTC)
                        statement.setTimestamp(5,
                            Timestamp.from(txDetails.commitTimestamp.plusSeconds(100)), tzUTC)
                        statement.setString(6, txDetails.result.toString())

                        statement.addBatch()
                        println(statement)
                        txDetails
                    }


                    assertThat(statement.executeBatch().sum()).isEqualTo(n)
                    connection.commit()

                    return results
                }
        }
    }

    private fun randomBytes(): ByteArray {
        return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
    }

    data class StateDetails(val sRef: UniquenessCheckStateRef, val consumingId: SecureHash?):
        UniquenessCheckStateDetails {
        override fun getStateRef() = sRef
        override fun getConsumingTxId(): SecureHash? = consumingId
    }

    data class StateRef(val hash: SecureHash, val index: Int):
        UniquenessCheckStateRef {
        override fun getTxHash(): SecureHash = hash
        override fun getStateIndex(): Int = index
    }

    data class TransactionDetails(
        val txIdAlgo: String,
        val txId: ByteArray,
        val originatorX500Name: String,
        val commitTimestamp: Instant,
        val result: Char
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TransactionDetails

            if (txIdAlgo != other.txIdAlgo) return false
            if (!txId.contentEquals(other.txId)) return false
            if (originatorX500Name != other.originatorX500Name) return false
            if (commitTimestamp != other.commitTimestamp) return false
            if (result != other.result) return false

            return true
        }

        override fun hashCode(): Int {
            var result1 = txIdAlgo.hashCode()
            result1 = 31 * result1 + txId.contentHashCode()
            result1 = 31 * result1 + originatorX500Name.hashCode()
            result1 = 31 * result1 + commitTimestamp.hashCode()
            result1 = 31 * result1 + result.hashCode()
            return result1
        }
    }
}