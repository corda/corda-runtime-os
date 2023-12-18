package net.corda.chunking.db.impl.tests

import com.google.common.jimfs.Jimfs
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.db.impl.persistence.database.DatabaseCpiPersistence
import net.corda.crypto.core.SecureHashImpl
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
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
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.network.writer.NetworkInfoWriter
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Random
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpsertCpiTests {
    companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/config/db.changelog-master.xml"
    }

    // N.B.  We're pulling in the config tables as well.
    private val emConfig = DbUtils.getEntityManagerConfiguration("chunking_db_for_test")
    private val entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
        "test_unit",
        ChunkingEntities.classes.toList() + CpiEntities.classes.toList(),
        emConfig
    )

    private val cpiSignerSummaryHash = SecureHashImpl("SHA-256", "signerSummaryHash".toByteArray())

    private val networkInfoWriter: NetworkInfoWriter = mock()

    private val cpiMetadataRepository = CpiCpkRepositoryFactory().createCpiMetadataRepository()

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
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
        entityManagerFactory.close()
    }

    lateinit var fs: FileSystem

    @BeforeEach
    fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    fun afterEach() = fs.close()

    private val cpiPersistence =
        DatabaseCpiPersistence(entityManagerFactory, networkInfoWriter, cpiMetadataRepository, mock(), mock(), mock(), mock())

    private fun String.writeToPath(): Path {
        val path = fs.getPath(UUID.randomUUID().toString())
        Files.writeString(path, this)
        return path
    }

    fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun newRandomSecureHash(): SecureHash {
        val random = Random()
        return SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    }

    /** Mock cpk with random string content **/
    private fun mockCpk(
        name: String,
        fileChecksum: SecureHash = newRandomSecureHash(),
        cpkSignerSummaryHash: SecureHash = newRandomSecureHash()
    ) = mock<Cpk>().also { cpk ->
        val cpkId = CpkIdentifier(name, "cpk-version", cpkSignerSummaryHash)

        val cordappManifest = CordappManifest(
            "", "", -1, -1,
            CordappType.WORKFLOW, "", "", -1, "",
            emptyMap()
        )

        val metadata = CpkMetadata(
            cpkId = cpkId,
            manifest = CpkManifest(CpkFormatVersion(1, 0)),
            mainBundle = "main-bundle",
            libraries = emptyList(),
            cordappManifest = cordappManifest,
            type = CpkType.UNKNOWN,
            fileChecksum = fileChecksum,
            cordappCertificates = emptySet(),
            timestamp = Instant.now(),
            externalChannelsConfig = "{}"
        )
        whenever(cpk.path).thenReturn(getRandomString(1024).writeToPath())
        whenever(cpk.originalFileName).thenReturn(name)
        whenever(cpk.metadata).thenReturn(metadata)
    }

    private fun mockCpiWithId(cpks: Collection<Cpk>, cpiId: CpiIdentifier, groupId: String): Cpi {
        val metadata = mock<CpiMetadata>().also {
            whenever(it.cpiId).thenReturn(cpiId)
            whenever(it.groupPolicy).thenReturn("""{"${GroupPolicyConstants.PolicyKeys.Root.GROUP_ID}": "$groupId"}""")
            whenever(it.fileChecksum).thenReturn(newRandomSecureHash())
        }

        val cpi = mock<Cpi>().also {
            whenever(it.cpks).thenReturn(cpks)
            whenever(it.metadata).thenReturn(metadata)
        }

        return cpi
    }

    /**
     * Persist a sort-of random Cpi with the given name, version and group id.
     *
     * @return the Cpi we persisted to the database.
     */
    private fun persistCpi(
        name: String,
        version: String,
        groupId: String,
        cpiPersistence: DatabaseCpiPersistence = this.cpiPersistence
    ): Cpi {
        val cpks = listOf(mockCpk("${UUID.randomUUID()}.cpk", newRandomSecureHash()))
        val id = CpiIdentifier(name, version, newRandomSecureHash())
        val cpi = mockCpiWithId(cpks, id, groupId)

        cpiPersistence.persistMetadataAndCpks(
            cpi, "test.cpi", newRandomSecureHash(), UUID.randomUUID().toString(), groupId, emptyList()
        )
        return cpi
    }

    private fun findCpiMetadata(cpi: Cpi): CpiMetadata? {
        val entity = entityManagerFactory.createEntityManager().transaction { em ->
            cpiMetadataRepository.findById(em, cpi.metadata.cpiId)
        }
        return entity
    }

    @Test
    fun `can insert and find a cpi`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val cpi = persistCpi(name, version, groupId)

        val cpiMetadata = findCpiMetadata(cpi)

        assertThat(cpiMetadata).isNotNull
        assertThat(cpiMetadata!!.cpiId.name).isEqualTo(cpi.metadata.cpiId.name)
        assertThat(cpiMetadata.cpiId.version).isEqualTo(cpi.metadata.cpiId.version)
        assertThat(GroupPolicyParser.groupIdFromJson(cpiMetadata.groupPolicy!!)).isEqualTo(groupId)
    }

    @Test
    fun `can insert a cpi into an empty database`() {
        assertDoesNotThrow {
            cpiPersistence.validateCanUpsertCpi(
                CpiIdentifier("anything", "any version", cpiSignerSummaryHash),
                "any group",
                forceUpload = false,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `cannot force update a cpi ithat doesn't exist`() {
        assertThrows<ValidationException> {
            cpiPersistence.validateCanUpsertCpi(
                CpiIdentifier("anything" + UUID.randomUUID().toString(), "any version", cpiSignerSummaryHash),
                "any group",
                forceUpload = true,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `can force update cpi with same name, signer version and group id`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val groupPolicyParser = mock<GroupPolicyParser.Companion> {
            on { groupIdFromJson(any()) } doReturn (groupId)
        }
        val cpiPersistence =
            DatabaseCpiPersistence(
                entityManagerFactory, networkInfoWriter, cpiMetadataRepository, mock(), mock(), mock(), groupPolicyParser)

        val cpi = persistCpi(name, version, groupId, cpiPersistence)
        val cpiMetadata = findCpiMetadata(cpi)
        assertThat(cpiMetadata).isNotNull

        assertDoesNotThrow {
            cpiPersistence.validateCanUpsertCpi(
                cpi.metadata.cpiId,
                groupId,
                forceUpload = true,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `cannot force update cpi with same name, signer version and different group id`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val cpi = persistCpi(name, version, groupId)
        val cpiMetadata = findCpiMetadata(cpi)
        assertThat(cpiMetadata).isNotNull

        assertThrows<ValidationException> {
            cpiPersistence.validateCanUpsertCpi(
                cpi.metadata.cpiId,
                groupId + "_2",
                forceUpload = true,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `cannot insert cpis with different group ids and same name, signer and version`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val cpi = persistCpi(name, version, groupId)
        val cpiMetadata = findCpiMetadata(cpi)
        assertThat(cpiMetadata).isNotNull

        assertThrows<DuplicateCpiUploadException> {
            cpiPersistence.validateCanUpsertCpi(
                cpi.metadata.cpiId,
                groupId + "_2",
                forceUpload = false,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `can insert cpis with same group id and different name`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val cpi = persistCpi(name, version, groupId)
        val cpiMetadata = findCpiMetadata(cpi)
        assertThat(cpiMetadata).isNotNull

        assertDoesNotThrow {
            cpiPersistence.validateCanUpsertCpi(
                cpi.metadata.cpiId.copy(name = cpi.metadata.cpiId.name + UUID.randomUUID().toString()),
                groupId,
                forceUpload = false,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `can insert cpis with same group id and different version`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val cpi = persistCpi(name, version, groupId)
        val cpiMetadata = findCpiMetadata(cpi)
        assertThat(cpiMetadata).isNotNull

        assertDoesNotThrow {
            cpiPersistence.validateCanUpsertCpi(
                cpi.metadata.cpiId.copy(version = cpi.metadata.cpiId.version + UUID.randomUUID().toString()),
                groupId,
                forceUpload = false,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `can insert cpis with same group id and different signer`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val cpi = persistCpi(name, version, groupId)
        val cpiMetadata = findCpiMetadata(cpi)
        assertThat(cpiMetadata).isNotNull

        assertDoesNotThrow {
            cpiPersistence.validateCanUpsertCpi(
                cpi.metadata.cpiId.copy(signerSummaryHash = newRandomSecureHash()),
                groupId,
                forceUpload = false,
                requestId = "ID"
            )
        }
    }

    @Test
    fun `cannot insert or update duplicate CPI`() {
        val groupId = "abcdef"
        val name = "test"
        val version = "1.0"
        val cpi = persistCpi(name, version, groupId)
        val cpiMetadata = findCpiMetadata(cpi)
        assertThat(cpiMetadata).isNotNull

        assertThrows<DuplicateCpiUploadException> {
            cpiPersistence.validateCanUpsertCpi(
                cpi.metadata.cpiId,
                groupId,
                forceUpload = false,
                requestId = "ID"
            )
        }
    }
}
