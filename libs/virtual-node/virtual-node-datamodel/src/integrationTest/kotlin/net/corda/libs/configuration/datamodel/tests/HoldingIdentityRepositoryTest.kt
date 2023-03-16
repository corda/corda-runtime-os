package net.corda.libs.configuration.datamodel.tests

import net.corda.crypto.core.ShortHash
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepositoryImpl
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.test.util.TestRandom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoldingIdentityRepositoryTest {
    private val dbConfig = DbUtils.getEntityManagerConfiguration(this::class.java.simpleName)
    private val entityManagerFactory: EntityManagerFactory

    private companion object {
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
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            this::class.java.simpleName,
            VirtualNodeEntities.classes.toList() + CpiEntities.classes.toList() + ConfigurationEntities.classes.toList(),
            dbConfig
        )
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        dbConfig.close()
        entityManagerFactory.close()
    }

    @Test
    fun find() {
        val holdingIdentity = entityManagerFactory.createEntityManager().transaction { em ->
            val hi = VNodeTestUtils.newHoldingIdentityEntity("Fred")
            em.persist(hi)
            hi
        }

        val foundEntity = entityManagerFactory.createEntityManager().use {
            HoldingIdentityRepositoryImpl().find(it, ShortHash.of(holdingIdentity.holdingIdentityShortHash))
        }

        assertThat(foundEntity).isNotNull
        assertThat(foundEntity).isEqualTo(holdingIdentity.toHoldingIdentity())
    }

    @Test
    fun `find returns null when not in DB`() {
        entityManagerFactory.createEntityManager().transaction { em ->
            val hi = VNodeTestUtils.newHoldingIdentityEntity("Jon")
            em.persist(hi)
            hi
        }

        val foundEntity = entityManagerFactory.createEntityManager().use {
            HoldingIdentityRepositoryImpl().find(it, ShortHash.of(TestRandom.holdingIdentityShortHash()))
        }

        assertThat(foundEntity).isNull()
    }

    @Test
    fun put() {
        val hi = VNodeTestUtils.newHoldingIdentityEntity("Merinda")

        entityManagerFactory.createEntityManager().transaction {
            HoldingIdentityRepositoryImpl().put(
                it,
                hi.toHoldingIdentity(),
            )
        }
    }
}