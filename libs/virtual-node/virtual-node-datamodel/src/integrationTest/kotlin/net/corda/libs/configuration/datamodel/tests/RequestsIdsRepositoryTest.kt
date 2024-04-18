package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepository
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepositoryImpl
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Timestamp
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequestsIdsRepositoryTest {

    @Suppress("JoinDeclarationAndAssignment")
    private val dbConfig: EntityManagerConfiguration

    private val entityManagerFactory: EntityManagerFactory

    private companion object {
        private const val VNODE_VAULT_MIGRATION_FILE_LOCATION = "net/corda/db/schema/vnode-vault/db.changelog-master.xml"
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * [entityManagerFactory].
     */
    init {
        // System.setProperty("postgresPort", "5432")
        dbConfig = DbUtils.getEntityManagerConfiguration(this::class.java.simpleName)

        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf(VNODE_VAULT_MIGRATION_FILE_LOCATION),
                    DbSchema::class.java.classLoader
                )
            )
        )
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            this::class.java.simpleName,
            emptyList(),
            dbConfig
        )
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        dbConfig.close()
        entityManagerFactory.close()
    }

    @AfterEach
    fun cleanUpAfterEach() {
        entityManagerFactory.createEntityManager().transaction {
            it.createNativeQuery(
                "DELETE FROM ${DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE}"
            ).executeUpdate()
        }
    }

    private val requestsIdsRepository: RequestsIdsRepository = RequestsIdsRepositoryImpl()

    @Test
    fun `inserts into request ids table`() {
        val requestId1 = UUID.randomUUID().toString()
        val requestId2 = UUID.randomUUID().toString()
        entityManagerFactory.createEntityManager().transaction { em ->
            requestsIdsRepository.persist(requestId1, em)
        }

        Thread.sleep(1)

        entityManagerFactory.createEntityManager().transaction { em ->
            requestsIdsRepository.persist(requestId2, em)
        }

        val storedRequestIds = getStoredRequestIds()
        assertEquals(2, storedRequestIds.size)
        assertEquals(requestId1, storedRequestIds[0].first)
        assertEquals(requestId2, storedRequestIds[1].first)
        val request1Time = storedRequestIds[0].second
        val request2Time = storedRequestIds[1].second
        assertTrue(request1Time < request2Time)
    }

    @Test
    fun `deletes older requests`() {
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        val requestId1 = UUID.randomUUID()
        val requestId2 = UUID.randomUUID()
        dbConfig.dataSource.connection.use { con ->
            con.autoCommit = true
            con.prepareStatement(
                """
                INSERT INTO ${DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE}(request_id, insert_ts)
                VALUES (?,?)
                """.trimIndent()
            ).also {
                it.setString(1, requestId1.toString())
                it.setTimestamp(2, Timestamp.from(Instant.now().minusSeconds(10)), utcCalendar)
            }.executeUpdate()
            con.prepareStatement(
                """
                INSERT INTO ${DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE}(request_id, insert_ts)
                VALUES (?,?)
                """.trimIndent()
            ).also {
                it.setString(1, requestId2.toString())
                it.setTimestamp(2, Timestamp.from(Instant.now().plusSeconds(10)), utcCalendar)
            }.executeUpdate()
        }
        var storedRequestIds = getStoredRequestIds()
        assertEquals(2, storedRequestIds.size)
        requestsIdsRepository.deleteRequestsOlderThan(1, dbConfig.dataSource)

        storedRequestIds = getStoredRequestIds()
        assertEquals(1, storedRequestIds.size)
    }

    private fun getStoredRequestIds(): List<Pair<String, java.sql.Timestamp>> =
        dbConfig.dataSource.connection.use {
            val stmt = it.prepareStatement(
                "SELECT * FROM ${DbSchema.VNODE_PERSISTENCE_REQUEST_ID_TABLE} ORDER BY insert_ts"
            )

            return stmt.use {
                val rs = stmt.executeQuery()

                val list = mutableListOf<Pair<String, java.sql.Timestamp>>()
                while (rs.next()) {
                    list.add(
                        Pair(
                            rs.getString(1),
                            rs.getTimestamp(2)
                        )
                    )
                }
                list
            }
        }
}
