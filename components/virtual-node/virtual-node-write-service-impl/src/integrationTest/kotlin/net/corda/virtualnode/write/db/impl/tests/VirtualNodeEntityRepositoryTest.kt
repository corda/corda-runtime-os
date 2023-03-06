package net.corda.virtualnode.write.db.impl.tests

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
        val expectedCpiMetadata =
            CpiMetadataLite(cpiId, SecureHash.parse(fileChecksum), "Test Group ID", "Test Group Policy")

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
        val expectedCpiMetadata = CpiMetadataLite(cpiId, SecureHash.parse(fileChecksum), mgmGroupId, groupPolicy)

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
        Assertions.assertThat(repository.getCpiMetadataByChecksum(hexFileChecksum.substring(0, 12)))
            .isEqualTo(expectedCpiMetadata)
    }
}
