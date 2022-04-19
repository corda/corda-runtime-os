package net.corda.chunking.datamodel

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ChunkEntityTest {
    val emConfig = DbUtils.getEntityManagerConfiguration("chunking_db_for_test")
    private val entityManagerFactory: EntityManagerFactory

    companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * [entityManagerFactory].
     */
    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf(MIGRATION_FILE_LOCATION),
                    DbSchema::class.java.classLoader
                )
            )
        )
        emConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            ChunkingEntities.classes.toList(),
            emConfig
        )
    }

    @Suppress("Unused")
    @AfterAll
    private fun cleanup() {
        emConfig.close()
        entityManagerFactory.close()
    }

    fun randomString() = UUID.randomUUID().toString()


    @Test
    fun `can write an entity`() {
        val requestId = randomString()
        val data = randomString().toByteArray()
        val entity = ChunkEntity(requestId, null, null, 0, 0, data)

        entityManagerFactory.createEntityManager().transaction { it.persist(entity) }
    }

    @Test
    fun `can read an entity`() {
        val requestId = randomString()
        val data = "truffle shuffle".toByteArray()
        val entity = ChunkEntity(requestId, null, null, 0, 0, data)

        entityManagerFactory.createEntityManager().transaction { it.persist(entity) }

        val actual = entityManagerFactory.createEntityManager().transaction {
            it.createQuery("SELECT count(c) FROM ${ChunkEntity::class.simpleName} c WHERE c.requestId = :requestId")
                .setParameter("requestId", requestId)
                .singleResult as Long
        }

        assertThat(actual).isEqualTo(1)
    }

    @Test
    fun `columns are equal`() {
        val requestId = randomString()
        val data = "truffle shuffle".toByteArray()
        val entity = ChunkEntity(requestId, null, null, 0, 0, data)

        entityManagerFactory.createEntityManager().transaction {

            it.persist(entity)
        }

        val actual = entityManagerFactory.createEntityManager().transaction {
            it.createQuery("SELECT c FROM ${ChunkEntity::class.simpleName} c WHERE c.requestId = :requestId")
                .setParameter("requestId", requestId)
                .singleResult as ChunkEntity
        }

        assertThat(actual).isEqualToComparingFieldByField(entity)
    }
}
