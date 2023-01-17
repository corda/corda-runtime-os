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
import net.corda.libs.virtualnode.datamodel.VirtualNodeOperationEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeOperationState
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntity
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.TestRandom
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
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
        }

        val virtualNode = VirtualNodeEntity(entity.holdingIdentityShortHash, entity, name, version, hash)

        val holdingIdentityEntity = entityManagerFactory.createEntityManager()
            .transaction { em -> em.getReference(HoldingIdentityEntity::class.java, entity.holdingIdentityShortHash) }

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.persist(virtualNode)
        }

        val key = holdingIdentityEntity.holdingIdentityShortHash
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
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
        }

        val virtualNode = VirtualNodeEntity(holdingIdentityEntity.holdingIdentityShortHash, holdingIdentityEntity, name, version, hash)

        // Persist holding identity...
        entityManagerFactory.createEntityManager().transaction { em -> em.persist(holdingIdentityEntity) }

        // Now persist the virtual node but use the merge operation because we've
        // already persisted the holding identity (i.e. REST end-point - "create holding identity")
        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.merge(virtualNode)
        }

        val key = holdingIdentityEntity.holdingIdentityShortHash
        assertThat(virtualNode == entityManagerFactory.createEntityManager().find(VirtualNodeEntity::class.java, key))
    }

    @Test
    fun `can persist Virtual Node Entity with an in progress operation`() {
        val name = "Test CPI - ${UUID.randomUUID()}"
        val version = "1.0-${Instant.now().toEpochMilli()}"
        val hash = TestRandom.secureHash().toString()

        val cpiMetadata = VNodeTestUtils.newCpiMetadataEntity(name, version, hash)
        val entity = VNodeTestUtils.newHoldingIdentityEntity("test")
        val operationEntity = VirtualNodeOperationEntity(entity.holdingIdentityShortHash, "request-id", "data", VirtualNodeOperationState.IN_PROGRESS, Instant.now())

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DDL))
            em.persist(VNodeTestUtils.newDbConnection(UUID.randomUUID(), DbPrivilege.DML))
        }

        val virtualNode = VirtualNodeEntity(entity.holdingIdentityShortHash, entity, name, version, hash, operationInProgress = operationEntity)

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(cpiMetadata)
            em.persist(virtualNode)
        }

        val holdingIdentityEntity = entityManagerFactory.createEntityManager()
            .transaction { em -> em.getReference(HoldingIdentityEntity::class.java, entity.holdingIdentityShortHash) }

        val key = holdingIdentityEntity.holdingIdentityShortHash
        val foundVirtualNode = entityManagerFactory.createEntityManager().find(VirtualNodeEntity::class.java, key)
        assertThat(virtualNode == foundVirtualNode)
        assertThat(operationEntity == foundVirtualNode.operationInProgress)
        assertThat(operationEntity == entityManagerFactory.createEntityManager().find(VirtualNodeOperationEntity::class.java, key))
    }
}