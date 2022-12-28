package net.corda.libs.configuration.datamodel.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.virtualnode.datamodel.entities.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntityKey
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.TestRandom
import net.corda.virtualnode.VirtualNodeState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualNodeEntitiesIntegrationTest {
    private val dbConfig = DbUtils.getEntityManagerConfiguration(this::class.java.simpleName)
    private val entityManagerFactory: EntityManagerFactory

    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
        private val random = Random(0)
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * [entityManagerFactory].
     */
    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ChangeLogResourceFiles(
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
    fun `can persist and read back Holding Identity entity`() {
        val holdingIdentityShortHash = TestRandom.holdingIdentityShortHash()
        val holdingIdentity = HoldingIdentityEntity(
            holdingIdentityShortHash,
            "a=b",
            "OU=LLC, O=Bob, L=Dublin, C=IE",
            "${random.nextInt()}",
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(holdingIdentity)
        }

        assertEquals(
            holdingIdentity,
            entityManagerFactory.createEntityManager()
                .find(HoldingIdentityEntity::class.java, holdingIdentity.holdingIdentityShortHash)
        )
    }

    @Test
    fun `can persist and read back Virtual Node entity and holding identity in one transaction`() {
        val name = "Test CPI - ${UUID.randomUUID()}"
        val version = "1.0-${Instant.now().toEpochMilli()}"
        val hash = TestRandom.secureHash().toString()

        val cpiMetadata = VNodeTestUtils.newCpiMetadataEntity(name, version, hash)
        val entity = VNodeTestUtils.newHoldingIdentityEntity("test")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(VNodeTestUtils.newDbConnection(entity.cryptoDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(entity.cryptoDMLConnectionId!!, DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(entity.vaultDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(entity.vaultDMLConnectionId!!, DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(entity.uniquenessDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(entity.uniquenessDMLConnectionId!!, DbPrivilege.DML))
        }

        val holdingIdentityEntity = entityManagerFactory.createEntityManager()
            .transaction { em -> em.getReference(HoldingIdentityEntity::class.java, entity.holdingIdentityShortHash) }

        val virtualNode = VirtualNodeEntity(entity, name, version, hash, VirtualNodeState.ACTIVE.name)

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.persist(virtualNode)
        }

        val key = VirtualNodeEntityKey(holdingIdentityEntity, name, version, hash)

        assertThat(virtualNode == entityManagerFactory.createEntityManager().find(VirtualNodeEntity::class.java, key))
    }

    @Test
    fun `can persist and read back Virtual Node entity with holding identity in two transactions`() {
        val name = "Test CPI - ${UUID.randomUUID()}"
        val version = "1.0-${Instant.now().toEpochMilli()}"
        val hash = TestRandom.secureHash().toString()

        val cpiMetadata = VNodeTestUtils.newCpiMetadataEntity(name, version, hash)
        val holdingIdentityEntity = VNodeTestUtils.newHoldingIdentityEntity("test - ${UUID.randomUUID()}")

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(VNodeTestUtils.newDbConnection(holdingIdentityEntity.cryptoDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(holdingIdentityEntity.cryptoDMLConnectionId!!, DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(holdingIdentityEntity.vaultDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(holdingIdentityEntity.vaultDMLConnectionId!!, DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(holdingIdentityEntity.uniquenessDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(holdingIdentityEntity.uniquenessDMLConnectionId!!, DbPrivilege.DML))
        }

        // Persist holding identity...
        entityManagerFactory.createEntityManager().transaction { em -> em.persist(holdingIdentityEntity) }

        // Now persist the virtual node but use the merge operation because we've
        // already persisted the holding identity (i.e. REST end-point - "create holding identity")
        val virtualNode = VirtualNodeEntity(holdingIdentityEntity, name, version, hash, VirtualNodeState.ACTIVE.name)
        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.merge(virtualNode)
        }

        // Use a reference to *find* only - we do NOT need the other fields in the HoldingIdentityEntity
        // (and in fact, neither does hibernate - it only cares about the primary keys).
        val holdingIdentityReference = entityManagerFactory.createEntityManager()
            .transaction { em -> em.getReference(HoldingIdentityEntity::class.java, holdingIdentityEntity.holdingIdentityShortHash) }
        val key = VirtualNodeEntityKey(holdingIdentityReference, name, version, hash)

        assertThat(virtualNode == entityManagerFactory.createEntityManager().find(VirtualNodeEntity::class.java, key))
    }
}

