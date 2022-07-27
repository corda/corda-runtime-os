package net.corda.virtualnode.write.db.impl.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntityKey
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class VirtualNodeEntityRepositoryTest {
    private val emConfig = DbUtils.getEntityManagerConfiguration("chunking_db_for_test")
    private val entityManagerFactory: EntityManagerFactory
    private val repository: VirtualNodeEntityRepository

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
            VirtualNodeEntities.classes.toList() + ConfigurationEntities.classes.toList() + CpiEntities.classes.toList(),
            emConfig
        )
        repository = VirtualNodeEntityRepository(entityManagerFactory)
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
        entityManagerFactory.close()
    }

    @Test
    fun `can read CPI metadata`() {
        val hexFileChecksum = "123456ABCDEF123456"
        val fileChecksum = "TEST:$hexFileChecksum"
        val signerSummaryHash = "TEST:121212121212"
        val cpiId = CpiIdentifier("Test CPI", "1.0", SecureHash.create(signerSummaryHash))
        val expectedCpiMetadata = CpiMetadataLite(cpiId, hexFileChecksum, "Test Group ID", "Test Group Policy")

        val cpiMetadataEntity = with(expectedCpiMetadata) {
            CpiMetadataEntity(
                id.name,
                id.version,
                signerSummaryHash,
                "TestFile",
                fileChecksum,
                "Test Group Policy",
                "Test Group ID",
                "Request ID",
                emptySet(),
            )
        }

        entityManagerFactory.transaction {
            it.persist(cpiMetadataEntity)
        }

        // Search by full file checksum
        var cpiMetadata = repository.getCPIMetadataByChecksum(fileChecksum)
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by hex file checksum
        cpiMetadata = repository.getCPIMetadataByChecksum(fileChecksum)
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by partial file checksum
        // We should not match anything less than the 12-char 'short hash'
        cpiMetadata = repository.getCPIMetadataByChecksum("56ABCD")
        Assertions.assertThat(cpiMetadata).isNotEqualTo(expectedCpiMetadata)

        // Search by partial file checksum using different case
        // We should not match anything less than the 12-char 'short hash'
        cpiMetadata = repository.getCPIMetadataByChecksum("56AbCd")
        Assertions.assertThat(cpiMetadata).isNotEqualTo(expectedCpiMetadata)

        // Search by partial file checksum
        cpiMetadata = repository.getCPIMetadataByChecksum("123456ABCDEF")
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by partial file checksum using different case
        cpiMetadata = repository.getCPIMetadataByChecksum("123456AbCdEf")
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Noll returned if not found
        cpiMetadata = repository.getCPIMetadataByChecksum("111111")
        Assertions.assertThat(cpiMetadata).isNull()
    }

    @Test
    fun `cannot use empty or short cpi file checksum`() {
        val hexFileChecksum = "123456789012"
        val fileChecksum = "TEST:$hexFileChecksum"
        val signerSummaryHash = "TEST:121212121212"
        val cpiId = CpiIdentifier("Test CPI 2", "2.0", SecureHash.create(signerSummaryHash))
        val mgmGroupId = "Test Group ID 2"
        val groupPolicy = "Test Group Policy 2"
        val expectedCpiMetadata = CpiMetadataLite(cpiId, hexFileChecksum, mgmGroupId, groupPolicy)

        val cpiMetadataEntity = with(expectedCpiMetadata) {
            CpiMetadataEntity(
                id.name,
                id.version,
                signerSummaryHash,
                "TestFile",
                fileChecksum,
                groupPolicy,
                mgmGroupId,
                "Request ID",
                emptySet(),
            )
        }

        entityManagerFactory.transaction {
            it.persist(cpiMetadataEntity)
        }

        Assertions.assertThat(repository.getCPIMetadataByChecksum("")).isNull()
        Assertions.assertThat(repository.getCPIMetadataByChecksum("123456")).isNull()
        Assertions.assertThat(repository.getCPIMetadataByChecksum(hexFileChecksum.substring(0, 12))).isEqualTo(expectedCpiMetadata)
    }

    @Test
    fun `can read holding identity`() {
        val expectedHoldingIdentity = HoldingIdentity("X500 Name", "Group ID")

        val holdingIdentityEntity = with(expectedHoldingIdentity) {
            HoldingIdentityEntity(
                shortHash, fullHash, x500Name, groupId, null, null,
                null, null, null
            )
        }

        entityManagerFactory.transaction {
            it.persist(holdingIdentityEntity)
        }

        // Search by short hash
        var holdingIdentity = repository.getHoldingIdentity(expectedHoldingIdentity.shortHash)
        Assertions.assertThat(holdingIdentity).isEqualTo(expectedHoldingIdentity)

        // Noll returned if not found
        holdingIdentity = repository.getHoldingIdentity("111111")
        Assertions.assertThat(holdingIdentity).isNull()
    }

    @Test
    fun `can save holding identity`() {
        val entityManager = entityManagerFactory.createEntityManager()

        val expectedHoldingIdentity = HoldingIdentity("X500 Name 2", "Group ID")

        // Save holding identity and DB connections

        val updateActor = "updateActor"
        val description = "description"
        val config = "config"
        val vaultDdlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Vault DDL 1", DDL, Instant.now(), updateActor, description, config
        )
        val vaultDmlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Vault DML 1", DML, Instant.now(), updateActor, description, config
        )
        val cryptoDdlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Crypto DDL 1", DDL, Instant.now(), updateActor, description, config
        )
        val cryptoDmlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Crypto DML 1", DML, Instant.now(), updateActor, description, config
        )
        val expectedConnections = VirtualNodeDbConnections(
            vaultDdlConnection.id, vaultDmlConnection.id, cryptoDdlConnection.id, cryptoDmlConnection.id
        )

        entityManager.transaction {
            it.persist(vaultDdlConnection)
            it.persist(vaultDmlConnection)
            it.persist(cryptoDdlConnection)
            it.persist(cryptoDmlConnection)
            repository.putHoldingIdentity(it, expectedHoldingIdentity, expectedConnections)
        }

        val holdingIdentityEntity = entityManagerFactory.transaction {
            it.find(HoldingIdentityEntity::class.java, expectedHoldingIdentity.shortHash)
        }

        Assertions.assertThat(holdingIdentityEntity).isNotNull
        with(holdingIdentityEntity) {
            Assertions.assertThat(holdingIdentityShortHash).isEqualTo(expectedHoldingIdentity.shortHash)
            Assertions.assertThat(holdingIdentityFullHash).isEqualTo(expectedHoldingIdentity.fullHash)
            Assertions.assertThat(x500Name).isEqualTo(expectedHoldingIdentity.x500Name)
            Assertions.assertThat(mgmGroupId).isEqualTo(expectedHoldingIdentity.groupId)
            Assertions.assertThat(vaultDDLConnectionId).isEqualTo(expectedConnections.vaultDdlConnectionId)
            Assertions.assertThat(vaultDMLConnectionId).isEqualTo(expectedConnections.vaultDmlConnectionId)
            Assertions.assertThat(cryptoDDLConnectionId).isEqualTo(expectedConnections.cryptoDdlConnectionId)
            Assertions.assertThat(cryptoDMLConnectionId).isEqualTo(expectedConnections.cryptoDmlConnectionId)
        }
    }

    @Test
    fun `can update holding identity`() {
        val expectedHoldingIdentity = HoldingIdentity("X500 Name 3", "Group ID")

        // Save holding identity and DB connections

        val updateActor = "updateActor"
        val description = "description"
        val config = "config"
        val vaultDdlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Vault DDL 2", DDL, Instant.now(), updateActor, description, config
        )
        val vaultDmlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Vault DML 2", DML, Instant.now(), updateActor, description, config
        )
        val cryptoDdlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Crypto DDL 2", DDL, Instant.now(), updateActor, description, config
        )
        val cryptoDmlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Crypto DML 2", DML, Instant.now(), updateActor, description, config
        )
        val dbConnections = VirtualNodeDbConnections(
            vaultDdlConnection.id, vaultDmlConnection.id, cryptoDdlConnection.id, cryptoDmlConnection.id
        )

        entityManagerFactory.transaction {
            it.persist(vaultDdlConnection)
            it.persist(vaultDmlConnection)
            it.persist(cryptoDdlConnection)
            it.persist(cryptoDmlConnection)
            repository.putHoldingIdentity(it, expectedHoldingIdentity, dbConnections)
        }

        // Update holding identity's DB connections

        val newVaultDdlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Vault DDL 3", DDL, Instant.now(), updateActor, description, config
        )
        val newVaultDmlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Vault DML 3", DML, Instant.now(), updateActor, description, config
        )
        val newCryptoDdlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Crypto DDL 3", DDL, Instant.now(), updateActor, description, config
        )
        val newCryptoDmlConnection = DbConnectionConfig(
            UUID.randomUUID(), "Crypto DML 3", DML, Instant.now(), updateActor, description, config
        )

        val expectedConnections = VirtualNodeDbConnections(
            newVaultDdlConnection.id, newVaultDmlConnection.id, newCryptoDdlConnection.id, newCryptoDmlConnection.id
        )

        entityManagerFactory.transaction {
            it.persist(newVaultDdlConnection)
            it.persist(newVaultDmlConnection)
            it.persist(newCryptoDdlConnection)
            it.persist(newCryptoDmlConnection)
            repository.putHoldingIdentity(it, expectedHoldingIdentity, expectedConnections)
        }

        val holdingIdentityEntity = entityManagerFactory.transaction {
            it.find(HoldingIdentityEntity::class.java, expectedHoldingIdentity.shortHash)
        }

        Assertions.assertThat(holdingIdentityEntity).isNotNull
        with(holdingIdentityEntity) {
            Assertions.assertThat(holdingIdentityShortHash).isEqualTo(expectedHoldingIdentity.shortHash)
            Assertions.assertThat(holdingIdentityFullHash).isEqualTo(expectedHoldingIdentity.fullHash)
            Assertions.assertThat(x500Name).isEqualTo(expectedHoldingIdentity.x500Name)
            Assertions.assertThat(mgmGroupId).isEqualTo(expectedHoldingIdentity.groupId)
            Assertions.assertThat(vaultDDLConnectionId).isEqualTo(expectedConnections.vaultDdlConnectionId)
            Assertions.assertThat(vaultDMLConnectionId).isEqualTo(expectedConnections.vaultDmlConnectionId)
            Assertions.assertThat(cryptoDDLConnectionId).isEqualTo(expectedConnections.cryptoDdlConnectionId)
            Assertions.assertThat(cryptoDMLConnectionId).isEqualTo(expectedConnections.cryptoDmlConnectionId)
        }
    }

    @Test
    fun `can check that virtual node exists`() {
        val hexFileChecksum = "789012ABCDEF"
        val fileChecksum = "TEST:$hexFileChecksum"
        val signerSummaryHash = "TEST:121212121212"
        val cpiId = CpiIdentifier("Test CPI 2", "1.0", SecureHash.create(signerSummaryHash))
        val cpiMetadata = CpiMetadataLite(cpiId, hexFileChecksum, "Test Group ID", "Test Group Policy")
        val holdingIdentity = HoldingIdentity("X500 Name 4", "Group ID")

        val cpiMetadataEntity = with(cpiMetadata) {
            CpiMetadataEntity(
                id.name,
                id.version,
                signerSummaryHash,
                "TestFile",
                fileChecksum,
                "Test Group Policy",
                "Test Group ID",
                "Request ID",
                emptySet()
            )
        }
        val holdingIdentityEntity = with(holdingIdentity) {
            HoldingIdentityEntity(
                shortHash, fullHash, x500Name, groupId, null, null,
                null, null, null
            )
        }
        val virtualNodeEntity =
            VirtualNodeEntity(holdingIdentityEntity, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString(), "")

        entityManagerFactory.transaction {
            it.persist(cpiMetadataEntity)
            it.persist(holdingIdentityEntity)
            it.persist(virtualNodeEntity)
        }

        Assertions.assertThat(repository.virtualNodeExists(holdingIdentity, cpiId)).isTrue

        val nonExistingCpiId =
            CpiIdentifier("Non existing CPI", "1.0", SecureHash.create(signerSummaryHash))
        Assertions.assertThat(repository.virtualNodeExists(holdingIdentity, nonExistingCpiId)).isFalse
    }

    @Test
    fun `can save virtual node`() {
        val hexFileChecksum = "789012FEDCBA"
        val fileChecksum = "TEST:$hexFileChecksum"
        val signerSummaryHash = "TEST:121212121212"
        val cpiId = CpiIdentifier("Test CPI 3", "1.0", SecureHash.create(signerSummaryHash))
        val cpiMetadata = CpiMetadataLite(cpiId, hexFileChecksum, "Test Group ID", "Test Group Policy")
        val holdingIdentity = HoldingIdentity("X500 Name 5", "Group ID")

        val cpiMetadataEntity = with(cpiMetadata) {
            CpiMetadataEntity(
                id.name,
                id.version,
                signerSummaryHash,
                "TestFile",
                fileChecksum,
                "Test Group Policy",
                "Test Group ID",
                "Request ID",
                emptySet()
            )
        }
        val holdingIdentityEntity = with(holdingIdentity) {
            HoldingIdentityEntity(
                shortHash, fullHash, x500Name, groupId, null, null,
                null, null, null
            )
        }

        entityManagerFactory.transaction {
            it.persist(cpiMetadataEntity)
            it.persist(holdingIdentityEntity)
        }

        entityManagerFactory.transaction {
            repository.putVirtualNode(it, holdingIdentity, cpiId)
        }

        val key = VirtualNodeEntityKey(holdingIdentityEntity, cpiId.name, cpiId.version, signerSummaryHash)
        val virtualNodeEntity = entityManagerFactory.transaction {
            it.find(VirtualNodeEntity::class.java, key)
        }

        Assertions.assertThat(virtualNodeEntity).isNotNull
        with(virtualNodeEntity) {
            Assertions.assertThat(holdingIdentityEntity.holdingIdentityShortHash).isEqualTo(holdingIdentity.shortHash)
            Assertions.assertThat(cpiName).isEqualTo(cpiId.name)
            Assertions.assertThat(cpiVersion).isEqualTo(cpiId.version)
            Assertions.assertThat(cpiSignerSummaryHash).isEqualTo(signerSummaryHash)
        }

        // Save should be idempotent
        entityManagerFactory.transaction {
            repository.putVirtualNode(it, holdingIdentity, cpiId)
        }
    }

    @Test
    fun `group ID is generated when not defined for MGM`() {
        val hexFileChecksum = "123456ABCDEF"
        val fileChecksum = "TEST:$hexFileChecksum"
        val signerSummaryHash = "TEST:121212121212"
        val cpiId = CpiIdentifier("Test CPI 4", "1.0", SecureHash.create(signerSummaryHash))

        val cpiMetadataEntity = CpiMetadataEntity(
            cpiId.name,
            cpiId.version,
            signerSummaryHash,
            "TestFile",
            fileChecksum,
            "Test Group Policy",
            MGM_DEFAULT_GROUP_ID,
            "Request ID",
            emptySet(),
        )

        entityManagerFactory.transaction {
            it.persist(cpiMetadataEntity)
        }

        Assertions.assertThat(repository.getCPIMetadataByChecksum(fileChecksum)?.mgmGroupId)
            .isNotEqualTo(MGM_DEFAULT_GROUP_ID)
    }
}
