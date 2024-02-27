package net.corda.virtualnode.write.db.impl.tests

import net.corda.crypto.core.parseSecureHash
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.InputStream
import java.io.InputStream.nullInputStream
import java.security.cert.CertificateFactory
import java.time.Instant
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class VirtualNodeEntityRepositoryTest {
    private val emConfig = DbUtils.getEntityManagerConfiguration("chunking_db_for_test")
    private val entityManagerFactory: EntityManagerFactory
    private val repository: VirtualNodeEntityRepository
    private val cpiMetadataRepository = CpiCpkRepositoryFactory().createCpiMetadataRepository()
    private val exampleCpk = object : Cpk {
        override val originalFileName
            get() = "originalFileName.jar"
        override val metadata: CpkMetadata
            get() = CpkMetadata(
                CpkIdentifier(
                    "name",
                    "version",
                    parseSecureHash("SHA-256:1234567890")
                ),
                CpkManifest(CpkFormatVersion(2, 0)),
                "mainBundle",
                listOf("Library A", "Library B"),
                CordappManifest(
                    "bundleSymbolicName",
                    "bundleVersion",
                    1,
                    1000,
                    CordappType.WORKFLOW,
                    "shortName",
                    "vendor",
                    versionId = 1,
                    "licence",
                    mapOf("a" to "b")
                ),
                CpkType.UNKNOWN,
                parseSecureHash("SHA-256:1234567890"),
                setOf(
                    CertificateFactory.getInstance("X.509").generateCertificate(
                        """
                            -----BEGIN CERTIFICATE-----
                            MIIB7zCCAZOgAwIBAgIEFyV7dzAMBggqhkjOPQQDAgUAMFsxCzAJBgNVBAYTAkdC
                            MQ8wDQYDVQQHDAZMb25kb24xDjAMBgNVBAoMBUNvcmRhMQswCQYDVQQLDAJSMzEe
                            MBwGA1UEAwwVQ29yZGEgRGV2IENvZGUgU2lnbmVyMB4XDTIwMDYyNTE4NTI1NFoX
                            DTMwMDYyMzE4NTI1NFowWzELMAkGA1UEBhMCR0IxDzANBgNVBAcTBkxvbmRvbjEO
                            MAwGA1UEChMFQ29yZGExCzAJBgNVBAsTAlIzMR4wHAYDVQQDExVDb3JkYSBEZXYg
                            Q29kZSBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQDjSJtzQ+ldDFt
                            pHiqdSJebOGPZcvZbmC/PIJRsZZUF1bl3PfMqyG3EmAe0CeFAfLzPQtf2qTAnmJj
                            lGTkkQhxo0MwQTATBgNVHSUEDDAKBggrBgEFBQcDAzALBgNVHQ8EBAMCB4AwHQYD
                            VR0OBBYEFLMkL2nlYRLvgZZq7GIIqbe4df4pMAwGCCqGSM49BAMCBQADSAAwRQIh
                            ALB0ipx6EplT1fbUKqgc7rjH+pV1RQ4oKF+TkfjPdxnAAiArBdAI15uI70wf+xlL
                            zU+Rc5yMtcOY4/moZUq36r0Ilg==
                            -----END CERTIFICATE-----
                        """.trimIndent().byteInputStream()
                    )
                ),
                Instant.EPOCH,
                "externalChannelsConfig"
            )

        override fun getInputStream(): InputStream = nullInputStream()
        override fun getResourceAsStream(resourceName: String): InputStream = nullInputStream()
        override fun getMainBundle(): InputStream = nullInputStream()
    }

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
            setOf(exampleCpk.metadata.copy()),
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
                setOf(exampleCpk)
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

        entityManagerFactory.transaction { em ->
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
        Assertions.assertThat(
            repository.getCpiMetadataByChecksum(hexFileChecksum.substring(0, 12))
                ?.copy(timestamp = expectedCpiMetadata.timestamp)
        ) // Ignore the timestamp comparison
            .isEqualTo(expectedCpiMetadata)
    }
}
