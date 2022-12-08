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
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.ShortHash
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
        val cpiId = CpiIdentifier("Test CPI", "1.0", SecureHash.parse(signerSummaryHash))
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
        var cpiMetadata = repository.getCpiMetadataByChecksum(fileChecksum)
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by hex file checksum
        cpiMetadata = repository.getCpiMetadataByChecksum(fileChecksum)
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by partial file checksum
        // We should not match anything less than the 12-char 'short hash'
        cpiMetadata = repository.getCpiMetadataByChecksum("56ABCD")
        Assertions.assertThat(cpiMetadata).isNotEqualTo(expectedCpiMetadata)

        // Search by partial file checksum using different case
        // We should not match anything less than the 12-char 'short hash'
        cpiMetadata = repository.getCpiMetadataByChecksum("56AbCd")
        Assertions.assertThat(cpiMetadata).isNotEqualTo(expectedCpiMetadata)

        // Search by partial file checksum
        cpiMetadata = repository.getCpiMetadataByChecksum("123456ABCDEF")
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by partial file checksum using different case
        cpiMetadata = repository.getCpiMetadataByChecksum("123456AbCdEf")
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Noll returned if not found
        cpiMetadata = repository.getCpiMetadataByChecksum("111111")
        Assertions.assertThat(cpiMetadata).isNull()
    }

    @Test
    fun `cannot use empty or short cpi file checksum`() {
        val hexFileChecksum = "123456789012"
        val fileChecksum = "TEST:$hexFileChecksum"
        val signerSummaryHash = "TEST:121212121212"
        val cpiId = CpiIdentifier("Test CPI 2", "2.0", SecureHash.parse(signerSummaryHash))
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

        Assertions.assertThat(repository.getCpiMetadataByChecksum("")).isNull()
        Assertions.assertThat(repository.getCpiMetadataByChecksum("123456")).isNull()
        Assertions.assertThat(repository.getCpiMetadataByChecksum(hexFileChecksum.substring(0, 12))).isEqualTo(expectedCpiMetadata)
    }

//    @Test
//    fun `can read holding identity`() {
//        val expectedHoldingIdentity = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "Group ID")
//
//        val holdingIdentityEntity = with(expectedHoldingIdentity) {
//            HoldingIdentityEntity(
//                shortHash.value,
//                fullHash,
//                x500Name.toString(),
//                groupId,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null
//            )
//        }
//
//        entityManagerFactory.transaction {
//            it.persist(holdingIdentityEntity)
//        }
//
//        // Search by short hash
//        var holdingIdentity = repository.getHoldingIdentity(expectedHoldingIdentity.shortHash)
//        Assertions.assertThat(holdingIdentity).isEqualTo(expectedHoldingIdentity)
//
//        // Noll returned if not found
//        holdingIdentity = repository.getHoldingIdentity(ShortHash.of("1234567890ab"))
//        Assertions.assertThat(holdingIdentity).isNull()
//    }

//    @Test
//    fun `can save holding identity`() {
//        val entityManager = entityManagerFactory.createEntityManager()
//
//        val expectedHoldingIdentity = createTestHoldingIdentity("CN=Bob-2, O=Bob Corp, L=LDN, C=GB", "Group ID")
//
//        // Save holding identity and DB connections
//
//        val updateActor = "updateActor"
//        val description = "description"
//        val config = "config"
//        val vaultDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Vault DDL 1", DDL, Instant.now(), updateActor, description, config
//        )
//        val vaultDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Vault DML 1", DML, Instant.now(), updateActor, description, config
//        )
//        val cryptoDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Crypto DDL 1", DDL, Instant.now(), updateActor, description, config
//        )
//        val cryptoDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Crypto DML 1", DML, Instant.now(), updateActor, description, config
//        )
//        val uniquenessDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Uniqueness DDL 1", DDL, Instant.now(), updateActor, description, config
//        )
//        val uniquenessDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Uniqueness DML 1", DML, Instant.now(), updateActor, description, config
//        )
//        val expectedConnections = VirtualNodeDbConnections(
//            vaultDdlConnection.id,
//            vaultDmlConnection.id,
//            cryptoDdlConnection.id,
//            cryptoDmlConnection.id,
//            uniquenessDdlConnection.id,
//            uniquenessDmlConnection.id
//        )
//
//        entityManager.transaction {
//            it.persist(vaultDdlConnection)
//            it.persist(vaultDmlConnection)
//            it.persist(cryptoDdlConnection)
//            it.persist(cryptoDmlConnection)
//            it.persist(uniquenessDdlConnection)
//            it.persist(uniquenessDmlConnection)
//            repository.putHoldingIdentity(it, expectedHoldingIdentity, expectedConnections)
//        }
//
//        val holdingIdentityEntity = entityManagerFactory.transaction {
//            it.find(HoldingIdentityEntity::class.java, expectedHoldingIdentity.shortHash.value)
//        }
//
//        Assertions.assertThat(holdingIdentityEntity).isNotNull
//        with(holdingIdentityEntity) {
//            Assertions.assertThat(holdingIdentityShortHash).isEqualTo(expectedHoldingIdentity.shortHash.value)
//            Assertions.assertThat(holdingIdentityFullHash).isEqualTo(expectedHoldingIdentity.fullHash)
//            Assertions.assertThat(x500Name).isEqualTo(expectedHoldingIdentity.x500Name.toString())
//            Assertions.assertThat(mgmGroupId).isEqualTo(expectedHoldingIdentity.groupId)
//            Assertions.assertThat(vaultDDLConnectionId).isEqualTo(expectedConnections.vaultDdlConnectionId)
//            Assertions.assertThat(vaultDMLConnectionId).isEqualTo(expectedConnections.vaultDmlConnectionId)
//            Assertions.assertThat(cryptoDDLConnectionId).isEqualTo(expectedConnections.cryptoDdlConnectionId)
//            Assertions.assertThat(cryptoDMLConnectionId).isEqualTo(expectedConnections.cryptoDmlConnectionId)
//        }
//    }

//    @Test
//    fun `can update holding identity`() {
//        val expectedHoldingIdentity = createTestHoldingIdentity("CN=Bob-3, O=Bob Corp, L=LDN, C=GB", "Group ID")
//
//        // Save holding identity and DB connections
//
//        val updateActor = "updateActor"
//        val description = "description"
//        val config = "config"
//        val vaultDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Vault DDL 2", DDL, Instant.now(), updateActor, description, config
//        )
//        val vaultDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Vault DML 2", DML, Instant.now(), updateActor, description, config
//        )
//        val cryptoDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Crypto DDL 2", DDL, Instant.now(), updateActor, description, config
//        )
//        val cryptoDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Crypto DML 2", DML, Instant.now(), updateActor, description, config
//        )
//        val uniquenessDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Uniqueness DDL 2", DDL, Instant.now(), updateActor, description, config
//        )
//        val uniquenessDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Uniqueness DML 2", DML, Instant.now(), updateActor, description, config
//        )
//        val dbConnections = VirtualNodeDbConnections(
//            vaultDdlConnection.id,
//            vaultDmlConnection.id,
//            cryptoDdlConnection.id,
//            cryptoDmlConnection.id,
//            uniquenessDdlConnection.id,
//            uniquenessDmlConnection.id
//        )
//
//        entityManagerFactory.transaction {
//            it.persist(vaultDdlConnection)
//            it.persist(vaultDmlConnection)
//            it.persist(cryptoDdlConnection)
//            it.persist(cryptoDmlConnection)
//            it.persist(uniquenessDdlConnection)
//            it.persist(uniquenessDmlConnection)
//            repository.putHoldingIdentity(it, expectedHoldingIdentity, dbConnections)
//        }
//
//        // Update holding identity's DB connections
//
//        val newVaultDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Vault DDL 3", DDL, Instant.now(), updateActor, description, config
//        )
//        val newVaultDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Vault DML 3", DML, Instant.now(), updateActor, description, config
//        )
//        val newCryptoDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Crypto DDL 3", DDL, Instant.now(), updateActor, description, config
//        )
//        val newCryptoDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Crypto DML 3", DML, Instant.now(), updateActor, description, config
//        )
//        val newUniquenessDdlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Uniqueness DDL 3", DDL, Instant.now(), updateActor, description, config
//        )
//        val newUniquenessDmlConnection = DbConnectionConfig(
//            UUID.randomUUID(), "Uniqueness DML 3", DML, Instant.now(), updateActor, description, config
//        )
//
//        val expectedConnections = VirtualNodeDbConnections(
//            newVaultDdlConnection.id,
//            newVaultDmlConnection.id,
//            newCryptoDdlConnection.id,
//            newCryptoDmlConnection.id,
//            newUniquenessDdlConnection.id,
//            newUniquenessDmlConnection.id
//        )
//
//        entityManagerFactory.transaction {
//            it.persist(newVaultDdlConnection)
//            it.persist(newVaultDmlConnection)
//            it.persist(newCryptoDdlConnection)
//            it.persist(newCryptoDmlConnection)
//            it.persist(newUniquenessDdlConnection)
//            it.persist(newUniquenessDmlConnection)
//            repository.putHoldingIdentity(it, expectedHoldingIdentity, expectedConnections)
//        }
//
//        val holdingIdentityEntity = entityManagerFactory.transaction {
//            it.find(HoldingIdentityEntity::class.java, expectedHoldingIdentity.shortHash.value)
//        }
//
//        Assertions.assertThat(holdingIdentityEntity).isNotNull
//        with(holdingIdentityEntity) {
//            Assertions.assertThat(ShortHash.of(holdingIdentityShortHash)).isEqualTo(expectedHoldingIdentity.shortHash)
//            Assertions.assertThat(holdingIdentityFullHash).isEqualTo(expectedHoldingIdentity.fullHash)
//            Assertions.assertThat(x500Name).isEqualTo(expectedHoldingIdentity.x500Name.toString())
//            Assertions.assertThat(mgmGroupId).isEqualTo(expectedHoldingIdentity.groupId)
//            Assertions.assertThat(vaultDDLConnectionId).isEqualTo(expectedConnections.vaultDdlConnectionId)
//            Assertions.assertThat(vaultDMLConnectionId).isEqualTo(expectedConnections.vaultDmlConnectionId)
//            Assertions.assertThat(cryptoDDLConnectionId).isEqualTo(expectedConnections.cryptoDdlConnectionId)
//            Assertions.assertThat(cryptoDMLConnectionId).isEqualTo(expectedConnections.cryptoDmlConnectionId)
//        }
//    }

//    @Test
//    fun `can check that virtual node exists`() {
//        val hexFileChecksum = "789012ABCDEF"
//        val fileChecksum = "TEST:$hexFileChecksum"
//        val signerSummaryHash = "TEST:121212121212"
//        val cpiId = CpiIdentifier("Test CPI 2", "1.0", SecureHash.parse(signerSummaryHash))
//        val cpiMetadata = CpiMetadataLite(cpiId, hexFileChecksum, "Test Group ID", "Test Group Policy")
//        val holdingIdentity = createTestHoldingIdentity("CN=Bob-4, O=Bob Corp, L=LDN, C=GB", "Group ID")
//
//        val cpiMetadataEntity = with(cpiMetadata) {
//            CpiMetadataEntity(
//                id.name,
//                id.version,
//                signerSummaryHash,
//                "TestFile",
//                fileChecksum,
//                "Test Group Policy",
//                "Test Group ID",
//                "Request ID",
//                emptySet()
//            )
//        }
//        val holdingIdentityEntity = with(holdingIdentity) {
//            HoldingIdentityEntity(
//                shortHash.value,
//                fullHash,
//                x500Name.toString(),
//                groupId,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null
//            )
//        }
//        val virtualNodeEntity =
//            VirtualNodeEntity(holdingIdentityEntity, cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString(), "")
//
//        entityManagerFactory.transaction {
//            it.persist(cpiMetadataEntity)
//            it.persist(holdingIdentityEntity)
//            it.persist(virtualNodeEntity)
//        }
//
//        Assertions.assertThat(repository.virtualNodeExists(holdingIdentity, cpiId)).isTrue
//
//        val nonExistingCpiId =
//            CpiIdentifier("Non existing CPI", "1.0", SecureHash.parse(signerSummaryHash))
//        Assertions.assertThat(repository.virtualNodeExists(holdingIdentity, nonExistingCpiId)).isFalse
//    }

//    @Test
//    fun `can save virtual node`() {
//        val hexFileChecksum = "789012FEDCBA"
//        val fileChecksum = "TEST:$hexFileChecksum"
//        val signerSummaryHash = "TEST:121212121212"
//        val cpiId = CpiIdentifier("Test CPI 3", "1.0", SecureHash.parse(signerSummaryHash))
//        val cpiMetadata = CpiMetadataLite(cpiId, hexFileChecksum, "Test Group ID", "Test Group Policy")
//        val holdingIdentity = createTestHoldingIdentity("CN=Bob-5, O=Bob Corp, L=LDN, C=GB", "Group ID")
//
//        val cpiMetadataEntity = with(cpiMetadata) {
//            CpiMetadataEntity(
//                id.name,
//                id.version,
//                signerSummaryHash,
//                "TestFile",
//                fileChecksum,
//                "Test Group Policy",
//                "Test Group ID",
//                "Request ID",
//                emptySet()
//            )
//        }
//        val holdingIdentityEntity = with(holdingIdentity) {
//            HoldingIdentityEntity(
//                shortHash.value,
//                fullHash,
//                x500Name.toString(),
//                groupId,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null
//            )
//        }
//
//        entityManagerFactory.transaction {
//            it.persist(cpiMetadataEntity)
//            it.persist(holdingIdentityEntity)
//        }
//
//        entityManagerFactory.transaction {
//            repository.putVirtualNode(it, holdingIdentity, cpiId)
//        }
//
//        val key = VirtualNodeEntityKey(holdingIdentityEntity, cpiId.name, cpiId.version, signerSummaryHash)
//        val virtualNodeEntity = entityManagerFactory.transaction {
//            it.find(VirtualNodeEntity::class.java, key)
//        }
//
//        Assertions.assertThat(virtualNodeEntity).isNotNull
//        with(virtualNodeEntity) {
//            Assertions.assertThat(holdingIdentityEntity.holdingIdentityShortHash).isEqualTo(holdingIdentity.shortHash.value)
//            Assertions.assertThat(cpiName).isEqualTo(cpiId.name)
//            Assertions.assertThat(cpiVersion).isEqualTo(cpiId.version)
//            Assertions.assertThat(cpiSignerSummaryHash).isEqualTo(signerSummaryHash)
//        }
//
//        // Save should be idempotent
//        entityManagerFactory.transaction {
//            repository.putVirtualNode(it, holdingIdentity, cpiId)
//        }
//    }
}
