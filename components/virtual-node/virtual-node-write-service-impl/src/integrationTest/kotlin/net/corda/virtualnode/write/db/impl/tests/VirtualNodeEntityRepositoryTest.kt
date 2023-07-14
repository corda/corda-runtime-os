package net.corda.virtualnode.write.db.impl.tests

import java.time.Instant
import javax.persistence.EntityManagerFactory
import net.corda.crypto.core.parseSecureHash
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class VirtualNodeEntityRepositoryTest {
    private val emConfig = DbUtils.getEntityManagerConfiguration("chunking_db_for_test")
    private val entityManagerFactory: EntityManagerFactory
    private val repository: VirtualNodeEntityRepository
    private val cpiMetadataRepository = CpiCpkRepositoryFactory().createCpiMetadataRepository()

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
        repository = VirtualNodeEntityRepository(entityManagerFactory, CpiCpkRepositoryFactory().createCpiMetadataRepository())
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
        val expectedCpiMetadata = CpiMetadata(
            CpiIdentifier("Test CPI", "1.0", parseSecureHash(signerSummaryHash)),
                parseSecureHash(fileChecksum),
                emptySet(),
                "Test Group Policy",
                0,
                Instant.now()
        )

        entityManagerFactory.transaction { em ->
            cpiMetadataRepository.put(
                em,
                expectedCpiMetadata.cpiId.copy(signerSummaryHash = parseSecureHash(signerSummaryHash)),
                "TestFile",
                parseSecureHash(fileChecksum),
                "Test Group ID",
                "Test Group Policy",
                "Request ID",
                emptySet()
            )
        }

        // Search by full file checksum
        var cpiMetadata = repository.getCpiMetadataByChecksum(fileChecksum)
            ?.copy(timestamp = expectedCpiMetadata.timestamp) // Ignore the timestamp comparison
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by hex file checksum
        cpiMetadata = repository.getCpiMetadataByChecksum(fileChecksum)
            ?.copy(timestamp = expectedCpiMetadata.timestamp) // Ignore the timestamp comparison
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by partial file checksum
        // We should not match anything less than the 12-char 'short hash'
        cpiMetadata = repository.getCpiMetadataByChecksum("56ABCD")
            ?.copy(timestamp = expectedCpiMetadata.timestamp) // Ignore the timestamp comparison
        Assertions.assertThat(cpiMetadata).isNotEqualTo(expectedCpiMetadata)

        // Search by partial file checksum using different case
        // We should not match anything less than the 12-char 'short hash'
        cpiMetadata = repository.getCpiMetadataByChecksum("56AbCd")
            ?.copy(timestamp = expectedCpiMetadata.timestamp) // Ignore the timestamp comparison
        Assertions.assertThat(cpiMetadata).isNotEqualTo(expectedCpiMetadata)

        // Search by partial file checksum
        cpiMetadata = repository.getCpiMetadataByChecksum("123456ABCDEF")
            ?.copy(timestamp = expectedCpiMetadata.timestamp) // Ignore the timestamp comparison
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Search by partial file checksum using different case
        cpiMetadata = repository.getCpiMetadataByChecksum("123456AbCdEf")
            ?.copy(timestamp = expectedCpiMetadata.timestamp) // Ignore the timestamp comparison
        Assertions.assertThat(cpiMetadata).isEqualTo(expectedCpiMetadata)

        // Null returned if not found
        cpiMetadata = repository.getCpiMetadataByChecksum("111111")
            ?.copy(timestamp = expectedCpiMetadata.timestamp) // Ignore the timestamp comparison
        Assertions.assertThat(cpiMetadata).isNull()
    }

    @Test
    fun `cannot use empty or short cpi file checksum`() {
        val hexFileChecksum = "123456789012"
        val fileChecksum = "TEST:$hexFileChecksum"
        val signerSummaryHash = "TEST:121212121212"
        val cpiId = CpiIdentifier("Test CPI 2", "2.0", parseSecureHash(signerSummaryHash))
        val mgmGroupId = "Test Group ID 2"
        val groupPolicy = "Test Group Policy 2"
        val expectedCpiMetadata = CpiMetadata(cpiId, parseSecureHash(fileChecksum), emptySet(), groupPolicy, 0, Instant.now())

        entityManagerFactory.transaction {em ->
            cpiMetadataRepository.put(
                em,
                expectedCpiMetadata.cpiId.copy(signerSummaryHash = parseSecureHash(signerSummaryHash)),
                "TestFile",
                parseSecureHash(fileChecksum),
                mgmGroupId,
                groupPolicy,
                "Request ID",
                emptySet()
            )
        }

        Assertions.assertThat(repository.getCpiMetadataByChecksum("")).isNull()
        Assertions.assertThat(repository.getCpiMetadataByChecksum("123456")).isNull()
        Assertions.assertThat(repository.getCpiMetadataByChecksum(hexFileChecksum.substring(0, 12))
            ?.copy(timestamp = expectedCpiMetadata.timestamp))  // Ignore the timestamp comparison
            .isEqualTo(expectedCpiMetadata)
    }
}
