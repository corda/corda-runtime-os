package net.corda.chunking.db.impl.tests

import com.google.common.jimfs.Jimfs
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.db.impl.persistence.PersistenceUtils.toCpkKey
import net.corda.chunking.db.impl.persistence.database.DatabaseCpiPersistence
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiCpkEntity
import net.corda.libs.cpi.datamodel.CpiCpkKey
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkDbChangeLogAuditEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogKey
import net.corda.libs.cpi.datamodel.CpkFileEntity
import net.corda.libs.cpi.datamodel.CpkKey
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.cpi.datamodel.QUERY_NAME_UPDATE_CPK_FILE_DATA
import net.corda.libs.cpi.datamodel.QUERY_PARAM_DATA
import net.corda.libs.cpi.datamodel.QUERY_PARAM_ENTITY_VERSION
import net.corda.libs.cpi.datamodel.QUERY_PARAM_FILE_CHECKSUM
import net.corda.libs.cpi.datamodel.QUERY_PARAM_ID
import net.corda.libs.cpi.datamodel.QUERY_PARAM_INCREMENTED_ENTITY_VERSION
import net.corda.libs.cpi.datamodel.findDbChangeLogAuditForCpi
import net.corda.libs.cpi.datamodel.findDbChangeLogForCpi
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
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Random
import java.util.UUID
import javax.persistence.PersistenceException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DatabaseCpiPersistenceTest {
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
    private val cpiPersistence = DatabaseCpiPersistence(entityManagerFactory)
    private val mockCpkContent = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin id mauris ut tortor 
            condimentum porttitor. Praesent commodo, ipsum vitae malesuada placerat, nisl sem 
            ornare nibh, id rutrum mi elit in metus. Sed ac tincidunt elit. Aliquam quis 
            pellentesque lacus. Quisque commodo tristique pellentesque. Nam sodales, urna id 
            convallis condimentum, nulla lacus vestibulum ipsum, et ultrices sem magna sed neque. 
            Pellentesque id accumsan odio, non interdum nibh. Nullam lacinia vestibulum purus, 
            finibus maximus enim scelerisque eu. Ut nibh lacus, semper eget cursus a, porttitor 
            eu odio. Vivamus vel placerat eros, sed convallis est. Proin tristique ut odio at 
            finibus. 
    """.trimIndent()
    private val mockChangeLogContent = "lorum ipsum"
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
    fun afterEach() {
        fs.close()
    }

    private fun String.writeToPath(): Path {
        val path = fs.getPath(UUID.randomUUID().toString())
        Files.writeString(path, this)
        return path
    }

    private fun updatedCpk(cpkId: CpkIdentifier, newFileChecksum: SecureHash = newRandomSecureHash()) =
        mockCpk(cpkId.name, newFileChecksum, cpkId.signerSummaryHash)

    private fun mockCpk(
        name: String? = null,
        fileChecksum: SecureHash? = null,
        cpkSignerSummaryHash: SecureHash? = null,
        version: String? = null
    ) = mock<Cpk>().also { cpk ->
        val nameDefined = name ?: "${UUID.randomUUID()}.cpk"
        val cpkId = CpkIdentifier(
            name = nameDefined,
            version = version ?: "cpk-version",
            signerSummaryHash = cpkSignerSummaryHash ?: newRandomSecureHash()
        )

        val cpkManifest = CpkManifest(CpkFormatVersion(1, 0))

        val cordappManifest = CordappManifest(
            "", "", -1, -1,
            CordappType.WORKFLOW, "", "", 0, "",
            emptyMap()
        )

        val metadata = CpkMetadata(
            cpkId = cpkId,
            manifest = cpkManifest,
            mainBundle = "main-bundle",
            libraries = emptyList(),
            cordappManifest = cordappManifest,
            type = CpkType.UNKNOWN,
            fileChecksum = fileChecksum ?: newRandomSecureHash(),
            cordappCertificates = emptySet(),
            timestamp = Instant.now()
        )
        whenever(cpk.path).thenReturn(mockCpkContent.writeToPath())
        whenever(cpk.originalFileName).thenReturn(nameDefined)
        whenever(cpk.metadata).thenReturn(metadata)
    }

    private fun mockCpi(
        vararg cpks: Cpk,
        signerSummaryHash: SecureHash? = null,
        name: String? = null,
        version: String? = null
    ): Cpi {
        val nameDefined = name ?: "test " + UUID.randomUUID().toString()
        // We need a random name here as the database primary key is (name, version, signerSummaryHash)
        // and we'd end up trying to insert the same mock cpi.
        val id = mock<CpiIdentifier> {
            whenever(it.name).thenReturn(nameDefined)
            whenever(it.version).thenReturn(version ?: "1.0")
            whenever(it.signerSummaryHash).thenReturn(signerSummaryHash ?: SecureHash("SHA-256", ByteArray(12)))
        }

        return mockCpiWithId(cpks.toList(), id)
    }

    private fun mockCpiWithId(cpks: List<Cpk>, cpiId: CpiIdentifier): Cpi {
        val checksum = newRandomSecureHash()
        val metadata = mock<CpiMetadata>().also {
            whenever(it.cpiId).thenReturn(cpiId)
            whenever(it.groupPolicy).thenReturn("{}")
            whenever(it.fileChecksum).thenReturn(checksum)
        }

        val cpi = mock<Cpi>().also {
            whenever(it.cpks).thenReturn(cpks.toList())
            whenever(it.metadata).thenReturn(metadata)
        }

        return cpi
    }

    /**
     * Various db tools show a persisted cpk (or bytes) as just a textual 'handle' to the blob of bytes,
     * so explicitly test here that it's actually doing what we think it is (persisting the bytes!).
     */
    @Test
    fun `database cpi persistence writes data and can be read back`() {
        val cpi = mockCpi(mockCpk())
        cpiPersistence.storeWithTestDefaults(cpi)

        val cpkDataEntities: List<CpkFileEntity> = query("fileChecksum", cpi.cpks.first().csum)
        assertThat(cpkDataEntities.first().data).isEqualTo(mockCpkContent.toByteArray())
    }

    @Test
    fun `database cpi persistence can lookup persisted cpi by checksum`() {
        val cpk = mockCpk()
        assertThat(cpiPersistence.cpkExists(cpk.metadata.fileChecksum)).isFalse
        val cpi = mockCpi(cpk)
        cpiPersistence.storeWithTestDefaults(cpi, "someFileName.cpi")
        assertThat(cpiPersistence.cpkExists(cpk.metadata.fileChecksum)).isTrue
    }

    @Test
    fun `database cpi persistence can write multiple cpks into database`() {
        val cpi = mockCpi(mockCpk(), mockCpk(), mockCpk())
        cpiPersistence.storeWithTestDefaults(cpi)
        assertThrows<PersistenceException> { cpiPersistence.storeWithTestDefaults(cpi) }
    }

    @Test
    fun `database cpi persistence can write multiple CPIs with shared CPKs into database`() {
        val sharedCpk = mockCpk()
        val cpk1 = mockCpk()
        val cpk2 = mockCpk()

        val cpi1 = mockCpi(sharedCpk, cpk1)
        cpiPersistence.storeWithTestDefaults(cpi1)

        val cpi2 = mockCpi(sharedCpk, cpk2)
        assertDoesNotThrow {
            cpiPersistence.storeWithTestDefaults(cpi2)
        }

        // no updates to existing CPKs have occurred hence why all entity versions are 0
        findAndAssertCpks(listOf(Pair(cpi1, sharedCpk), Pair(cpi2, sharedCpk), Pair(cpi1, cpk1), Pair(cpi2, cpk2)))
    }

    @Test
    fun `database cpi persistence can force update a CPI`() {
        val cpi = mockCpi(mockCpk())
        val cpiFileName =
            "test${UUID.randomUUID()}.cpi" // control the filename so we have a fresh entity with known version number
        val cpiMetadataEntity = cpiPersistence.storeWithTestDefaults(cpi, cpiFileName)

        assertThat(cpiMetadataEntity.entityVersion).isEqualTo(1)
        assertThat(cpiMetadataEntity.cpks.size).isEqualTo(1)
        assertThat(cpiMetadataEntity.cpks.first().entityVersion).isEqualTo(0)

        // make same assertions but after loading the entity again
        val initialLoadedCpi = loadCpiDirectFromDatabase(cpi)

        assertThat(initialLoadedCpi.entityVersion).isEqualTo(1)
        assertThat(initialLoadedCpi.cpks.size).isEqualTo(1)
        assertThat(initialLoadedCpi.cpks.first().entityVersion).isEqualTo(0)

        val updatedCpi = mockCpiWithId(listOf(cpi.cpks.first(), mockCpk()), cpi.metadata.cpiId)
        val returnedCpiMetadataEntity = cpiPersistence.storeWithTestDefaults(updatedCpi, forceCpiUpdate = true)

        fun verifyDoubleCpi(cpiMetadata: CpiMetadataEntity) {
            assertThat(cpiMetadata.cpks.size).isEqualTo(2)
            assertThat(cpiMetadata.entityVersion).isEqualTo(3)
            val firstReturnedCpk = cpiMetadata.cpks.first { it.cpkFileChecksum == cpi.cpks.first().csum }
            val secondReturnedCpk =
                cpiMetadata.cpks.first { it.cpkFileChecksum == updatedCpi.cpks.toTypedArray().get(1).csum }
            // JPA only increments entity version on the entities it is called on directly, not on embedded objects, and we insert
            // the CpiCpkEntity objects indirectly so they don't get modified, so are still at entityVersion=0
            assertThat(firstReturnedCpk.entityVersion).isEqualTo(0)
            assertThat(secondReturnedCpk.entityVersion).isEqualTo(0)
        }

        verifyDoubleCpi(returnedCpiMetadataEntity)
        val updatedLoadedCpi = loadCpiDirectFromDatabase(updatedCpi) // and check the same in the database
        verifyDoubleCpi(updatedLoadedCpi)
    }

    @Test
    fun `database cpi persistence can force update the same CPI`() {
        val cpiChecksum = newRandomSecureHash()
        val cpi = mockCpi(mockCpk())

        cpiPersistence.storeWithTestDefaults(cpi, "test.cpi", cpiChecksum)

        val loadedCpi = loadCpiDirectFromDatabase(cpi)

        // adding cpk to cpi accounts for 1 modification
        assertThat(loadedCpi.entityVersion).isEqualTo(1)
        assertThat(loadedCpi.cpks.size).isEqualTo(1)
        assertThat(loadedCpi.cpks.first().entityVersion).isEqualTo(0)

        cpiPersistence.storeWithTestDefaults(
            cpi,
            checksum = cpiChecksum,
            forceCpiUpdate = true
        )  // force update same CPI

        val updatedCpi = loadCpiDirectFromDatabase(cpi)

        assertThat(updatedCpi.insertTimestamp).isAfter(loadedCpi.insertTimestamp)
        // merging updated cpi accounts for 1 modification + modifying cpk
        assertThat(updatedCpi.entityVersion).isEqualTo(3)
        assertThat(updatedCpi.cpks.size).isEqualTo(1)
        assertThat(updatedCpi.cpks.first().entityVersion).isEqualTo(0)
    }

    @Test
    fun `CPKs are correct after persisting a CPI with already existing CPK`() {
        val sharedCpk = mockCpk()
        val cpi = mockCpi(sharedCpk)
        cpiPersistence.storeWithTestDefaults(cpi, groupId = "group-a")
        val cpi2 = mockCpi(sharedCpk)
        cpiPersistence.storeWithTestDefaults(cpi2, cpiFileName = "test2.cpi", groupId = "group-b")
        // no updates to existing CPKs have occurred hence why all entity versions are 0
        findAndAssertCpks(listOf(Pair(cpi, sharedCpk), Pair(cpi2, sharedCpk)))
    }

    @Test
    fun `CPKs are correct after updating a CPI by adding a new CPK`() {
        val cpi = mockCpi(mockCpk())
        cpiPersistence.storeWithTestDefaults(cpi, groupId = "group-a")
        // a new cpi object, but with same ID and added new CPK
        val updatedCpi = mockCpiWithId(listOf(cpi.cpks.first(), mockCpk()), cpi.metadata.cpiId)
        cpiPersistence.storeWithTestDefaults(updatedCpi, groupId = "group-b", forceCpiUpdate = true)
        assertThat(cpi.metadata.cpiId).isEqualTo(updatedCpi.metadata.cpiId)
        // no updates to existing CPKs have occurred hence why all entity versions are 0. We are updating a CPI by adding a new CPK to it
        findAndAssertCpks(listOf(Pair(cpi, cpi.cpks.first())), expectedCpiCpkEntityVersion = 0)
        findAndAssertCpks(listOf(Pair(cpi, updatedCpi.cpks.toTypedArray().get(1))))
    }

    @Test
    fun `CPK version is incremented when we update a CPK in a CPI`() {
        val cpi = mockCpi(mockCpk())
        val newChecksum = newRandomSecureHash()
        val updatedCpk = updatedCpk(cpi.cpks.first().metadata.cpkId, newChecksum)
        cpiPersistence.storeWithTestDefaults(cpi, groupId = "group-a")
        val updatedCpi = mockCpiWithId(listOf(updatedCpk), cpi.metadata.cpiId)  // a new cpi object, but with same ID
        cpiPersistence.storeWithTestDefaults(updatedCpi, groupId = "group-b", forceCpiUpdate = true)
        assertThat(cpi.metadata.cpiId).isEqualTo(updatedCpi.metadata.cpiId)
        // we have updated an existing CPK with a new checksum (and data) hence why its entityVersion has incremented.
        findAndAssertCpks(
            listOf(Pair(cpi, cpi.cpks.first())),
            expectedCpkFileChecksum = newChecksum.toString(),
            expectedMetadataEntityVersion = 1,
            expectedFileEntityVersion = 1,
            expectedCpiCpkEntityVersion = 1
        )
    }

    @Test
    fun `CPK version is incremented when CpiCpkEntity has non-zero entityversion`() {
        val cpi = mockCpi(mockCpk())
        cpiPersistence.storeWithTestDefaults(cpi, groupId = "group-a")
        findAndAssertCpks(listOf(Pair(cpi, cpi.cpks.first())))

        // a new cpi object, but with same cpk
        val secondCpkChecksum = newRandomSecureHash()
        val updatedCpk = updatedCpk(cpi.cpks.first().metadata.cpkId, secondCpkChecksum)
        val updatedCpi = mockCpiWithId(listOf(updatedCpk), cpi.metadata.cpiId)

        cpiPersistence.storeWithTestDefaults(updatedCpi, groupId = "group-b", forceCpiUpdate = true)

        // we have updated an existing CPK hence why the entity versions are incremented.
        findAndAssertCpks(
            listOf(Pair(cpi, cpi.cpks.first())),
            expectedCpkFileChecksum = updatedCpk.csum,
            expectedMetadataEntityVersion = 1,
            expectedFileEntityVersion = 1,
            expectedCpiCpkEntityVersion = 1
        )

        // a new cpi object, but with same cpk
        val thirdChecksum = newRandomSecureHash()
        val anotherUpdatedCpk = updatedCpk(cpi.cpks.first().metadata.cpkId, thirdChecksum)
        val anotherUpdatedCpi = mockCpiWithId(listOf(anotherUpdatedCpk), cpi.metadata.cpiId)

        cpiPersistence.storeWithTestDefaults(anotherUpdatedCpi, groupId = "group-b", forceCpiUpdate = true)

        // We have updated the same CPK again hence why the entity versions are incremented again.
        findAndAssertCpks(
            listOf(Pair(cpi, cpi.cpks.first())),
            expectedCpkFileChecksum = thirdChecksum.toString(),
            expectedMetadataEntityVersion = 2,
            expectedFileEntityVersion = 2,
            expectedCpiCpkEntityVersion = 2
        )
    }

    @Test
    fun `after CPK file has been persisted we can update its data using the CpkFileEntity updateFileData named query`() {

        val testId = UUID.randomUUID().toString()

        val firstCpkChecksum = newRandomSecureHash()
        val cpk = mockCpk("$testId.cpk", firstCpkChecksum)
        val cpi = mockCpi(cpk)

        cpiPersistence.storeWithTestDefaults(cpi, "$testId.cpi", groupId = "group-a")
        val cpkKey = cpk.metadata.cpkId.toCpkKey()
        val initialFile = entityManagerFactory.createEntityManager().transaction {
            it.find(CpkFileEntity::class.java, cpkKey)
        }

        val initialTimestamp = initialFile.insertTimestamp
        assertThat(initialFile.fileChecksum).isEqualTo(cpk.metadata.fileChecksum.toString())
        assertThat(initialFile.entityVersion).isEqualTo(0)

        val newCpkChecksum = newRandomSecureHash().toString()
        entityManagerFactory.createEntityManager().transaction {
            val entitiesUpdated = it.createNamedQuery(QUERY_NAME_UPDATE_CPK_FILE_DATA)
                .setParameter(QUERY_PARAM_FILE_CHECKSUM, newCpkChecksum)
                .setParameter(QUERY_PARAM_DATA, testId.toByteArray())
                .setParameter(QUERY_PARAM_ENTITY_VERSION, 0)
                .setParameter(QUERY_PARAM_INCREMENTED_ENTITY_VERSION, 1)
                .setParameter(QUERY_PARAM_ID, cpkKey)
                .executeUpdate()

            assertThat(entitiesUpdated)
                .withFailMessage("An error occurred invoking named query to update cpk file data.")
                .isEqualTo(1)
        }

        val file = entityManagerFactory.createEntityManager().transaction {
            it.find(CpkFileEntity::class.java, cpkKey)
        }

        assertThat(file.entityVersion).isEqualTo(1)
        assertThat(file.fileChecksum).isEqualTo(newCpkChecksum)
        assertThat(String(file.data)).isEqualTo(testId)
        assertThat(file.insertTimestamp)
            .withFailMessage("Insert timestamp should be updated")
            .isAfter(initialTimestamp)
    }

    @Test
    fun `force upload can remove all changelogs`() {
        val cpiWithChangelogs = mockCpi(mockCpk())
        val cpiEntity = cpiPersistence.storeWithTestDefaults(
            cpiWithChangelogs,
            cpkDbChangeLogEntities = makeChangeLogs(arrayOf(cpiWithChangelogs.cpks.first()))
        )

        fun findChangelogs(cpiEntity: CpiMetadataEntity) = entityManagerFactory.createEntityManager().transaction {
            findDbChangeLogForCpi(
                it, CpiIdentifier(
                    name = cpiEntity.name,
                    version = cpiEntity.version,
                    signerSummaryHash = SecureHash.parse(cpiEntity.signerSummaryHash)
                )
            )
        }

        val changelogsWith = findChangelogs(cpiEntity)
        assertThat(changelogsWith.size).isEqualTo(1)
        val updatedCpi = mockCpiWithId(listOf(mockCpk()), cpiWithChangelogs.metadata.cpiId)
        val updateCpiEntity = cpiPersistence.storeWithTestDefaults(updatedCpi, forceCpiUpdate = true)
        val changelogsWithout = findChangelogs(updateCpiEntity)
        assertThat(changelogsWithout.size).isEqualTo(0)
    }


    @Disabled("https://r3-cev.atlassian.net/browse/CORE-6068")
    @Test
    fun `cannot store multiple versions of the same CPI name in the same group`() {
        // Currently existing vnodes do not support upgrade. When that's ready, we should remove
        // or modify this test, for instance by removing the assertThatThrownBy and expecting the second
        // upload to work.
        val name = UUID.randomUUID().toString()
        val cpi_v1 = mockCpi(mockCpk(), name = name, version = "v1")
        val cpi_v2 = mockCpi(mockCpk(), name = name, version = "v2")
        cpiPersistence.storeWithTestDefaults(cpi_v1)
        assertThatThrownBy { cpiPersistence.storeWithTestDefaults(cpi_v2) }
    }

    @Test
    fun `force upload adds a new changelog audit entry`() {
        val cpi = mockCpi(mockCpk())
        val cpiEntity = cpiPersistence.persistMetadataAndCpks(
            cpi, "test.cpi", newRandomSecureHash(), UUID.randomUUID().toString(),
            "group-A", makeChangeLogs(arrayOf(cpi.cpks.first()))
        )

        fun findChangelogs(cpiEntity: CpiMetadataEntity) = entityManagerFactory.createEntityManager().transaction {
            findDbChangeLogForCpi(
                it,
                CpiIdentifier(
                    name = cpiEntity.name,
                    version = cpiEntity.version,
                    signerSummaryHash = SecureHash.parse(cpiEntity.signerSummaryHash)
                )
            )
        }

        fun findChangelogAudits(cpiEntity: CpiMetadataEntity) = entityManagerFactory.createEntityManager().transaction {
            findDbChangeLogAuditForCpi(
                it,
                CpiIdentifier(
                    name = cpiEntity.name,
                    version = cpiEntity.version,
                    signerSummaryHash = SecureHash.parse(cpiEntity.signerSummaryHash)
                )
            )
        }

        val changelogs = findChangelogs(cpiEntity)
        val changelogAudits = findChangelogAudits(cpiEntity)
        assertThat(changelogs.size).isEqualTo(1)
        assertThat(changelogAudits.size).isEqualTo(1)
        val updatedCpi = mockCpiWithId(listOf(mockCpk()), cpi.metadata.cpiId)
        val updateCpiEntity = cpiPersistence.updateMetadataAndCpks(
            updatedCpi, "test.cpi", newRandomSecureHash(), UUID.randomUUID().toString(), "group-A",
            makeChangeLogs(arrayOf(updatedCpi.cpks.first()), listOf("Something different"))
        )
        val updatedChangelogs = findChangelogs(updateCpiEntity)
        val updatedChangelogAudits = findChangelogAudits(updateCpiEntity)

        assertThat(updatedChangelogs.size).isEqualTo(1)
        assertThat(updatedChangelogAudits.size).isEqualTo(2)
        assertThat((changelogs + updatedChangelogs).map { CpkDbChangeLogAuditEntity(it).id })
            .containsAll(updatedChangelogAudits.map { it.id })
    }

    @Test
    fun `force upload adds multiple changelog audit entry for multiple changesets`() {
        val cpi = mockCpi(mockCpk())
        val cpiEntity = cpiPersistence.persistMetadataAndCpks(
            cpi, "test.cpi", newRandomSecureHash(), UUID.randomUUID().toString(),
            "group-A", makeChangeLogs(arrayOf(cpi.cpks.first()))
        )

        fun findChangelogs(cpiEntity: CpiMetadataEntity) = entityManagerFactory.createEntityManager().transaction {
            findDbChangeLogForCpi(
                it,
                CpiIdentifier(
                    name = cpiEntity.name,
                    version = cpiEntity.version,
                    signerSummaryHash = SecureHash.parse(cpiEntity.signerSummaryHash)
                )
            )
        }

        fun findChangelogAudits(cpiEntity: CpiMetadataEntity) = entityManagerFactory.createEntityManager().transaction {
            findDbChangeLogAuditForCpi(
                it,
                CpiIdentifier(
                    name = cpiEntity.name,
                    version = cpiEntity.version,
                    signerSummaryHash = SecureHash.parse(cpiEntity.signerSummaryHash)
                )
            )
        }

        val changelogs = findChangelogs(cpiEntity)
        val changelogAudits = findChangelogAudits(cpiEntity)
        assertThat(changelogs.size).isEqualTo(1)
        assertThat(changelogAudits.size).isEqualTo(1)
        val updatedCpi = mockCpiWithId(listOf(mockCpk()), cpi.metadata.cpiId)
        val updateCpiEntity = cpiPersistence.updateMetadataAndCpks(
            updatedCpi, "test.cpi", newRandomSecureHash(), UUID.randomUUID().toString(), "group-A",
            makeChangeLogs(arrayOf(updatedCpi.cpks.first()), listOf("Something different", "Something else"))
        )
        val updatedChangelogs = findChangelogs(updateCpiEntity)
        val updatedChangelogAudits = findChangelogAudits(updateCpiEntity)

        assertThat(updatedChangelogs.size).isEqualTo(2)
        assertThat(updatedChangelogAudits.size).isEqualTo(3)
        assertThat((changelogs + updatedChangelogs).map { CpkDbChangeLogAuditEntity(it).id })
            .containsAll(updatedChangelogAudits.map { it.id })
    }

    @Test
    fun `persist changelog writes data and can be read back`() {
        val cpi = mockCpi(mockCpk())
        cpiPersistence.storeWithTestDefaults(cpi, cpkDbChangeLogEntities = makeChangeLogs(arrayOf(cpi.cpks.first())))

        val changeLogsRetrieved = query<CpkDbChangeLogEntity, String>("cpk_name", cpi.cpks.first().metadata.cpkId.name)

        assertThat(changeLogsRetrieved.size).isGreaterThanOrEqualTo(1)
        assertThat(changeLogsRetrieved.first().content).isEqualTo(mockChangeLogContent)
    }

    @Test
    fun `persist multiple changelogs writes data and can be read back`() {
        val cpi = mockCpi(mockCpk(), mockCpk(), mockCpk(), mockCpk(), mockCpk())
        cpiPersistence.storeWithTestDefaults(cpi, cpkDbChangeLogEntities = makeChangeLogs(cpi.cpks.toTypedArray()))

        val changeLogsRetrieved = query<CpkDbChangeLogEntity, String>("content", mockChangeLogContent)

        assertThat(changeLogsRetrieved.size).isGreaterThanOrEqualTo(5)
        assertThat(changeLogsRetrieved.first().content).isEqualTo(mockChangeLogContent)
    }

    @Test
    fun `version number of changelog increases when changelogs are updated`() {
        val signerSummaryHash = newRandomSecureHash()
        val name = "${UUID.randomUUID()}.cpk"
        val cpks = (1..5).map { mockCpk(name = name, cpkSignerSummaryHash = signerSummaryHash) }
        for ((i, cpk) in cpks.withIndex()) {
            val cpi = mockCpi(cpk, signerSummaryHash = signerSummaryHash)
            cpiPersistence.storeWithTestDefaults(cpi, cpkDbChangeLogEntities = makeChangeLogs(cpks = arrayOf(cpk)))
            val allTestCpks = query<CpkDbChangeLogEntity, String>("cpk_name", cpk.originalFileName!!)
            assertThat(allTestCpks.size).isGreaterThan(0)
            val changeLog =
                query<CpkDbChangeLogEntity, String>(
                    "cpk_signer_summary_hash",
                    signerSummaryHash.toString()
                ).first()
            assertThat(changeLog.entityVersion).isEqualTo(i)
        }
    }

    private inline fun <reified T : Any, K> query(key: String, value: K): List<T> {
        val query = "FROM ${T::class.simpleName} where $key = :value"
        return entityManagerFactory.createEntityManager().transaction {
            it.createQuery(query, T::class.java)
                .setParameter("value", value)
                .resultList
        }!!
    }
    
    private val random = Random(0)
    private fun newRandomSecureHash(): SecureHash {
        return SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
    }

    private fun makeChangeLogs(
        cpks: Array<Cpk>,
        changeLogs: List<String> = listOf(mockChangeLogContent)
    ): List<CpkDbChangeLogEntity> = cpks.flatMap {
        changeLogs.map { changeLog ->
            CpkDbChangeLogEntity(
                CpkDbChangeLogKey(
                    it.metadata.cpkId.name,
                    it.metadata.cpkId.version,
                    it.metadata.cpkId.signerSummaryHash.toString(),
                    "resources/$changeLog"
                ),
                newRandomSecureHash().toString(),
                changeLog,
                UUID.randomUUID()
            )
        }
    }

    private fun loadCpiDirectFromDatabase(cpi: Cpi): CpiMetadataEntity =
        entityManagerFactory.createEntityManager().transaction {
            it.find(
                CpiMetadataEntity::class.java, CpiMetadataEntityKey(
                    cpi.metadata.cpiId.name,
                    cpi.metadata.cpiId.version,
                    cpi.metadata.cpiId.signerSummaryHash.toString(),
                )
            )
        }!!

    private fun findAndAssertCpks(
        combos: List<Pair<Cpi, Cpk>>,
        expectedCpkFileChecksum: String? = null,
        expectedMetadataEntityVersion: Int = 0,
        expectedFileEntityVersion: Int = 0,
        expectedCpiCpkEntityVersion: Int = 0
    ) {
        combos.forEach { (cpi, cpk) ->
            val (cpkMetadata, cpkFile, cpiCpk) = entityManagerFactory.createEntityManager().transaction {
                val cpiCpkKey = CpiCpkKey(
                    cpi.metadata.cpiId.name,
                    cpi.metadata.cpiId.version,
                    cpi.metadata.cpiId.signerSummaryHash.toString(),
                    cpk.metadata.cpkId.name,
                    cpk.metadata.cpkId.version,
                    cpk.metadata.cpkId.signerSummaryHash.toString()
                )
                val cpkKey = CpkKey(
                    cpk.metadata.cpkId.name,
                    cpk.metadata.cpkId.version,
                    cpk.metadata.cpkId.signerSummaryHash.toString()
                )
                val cpiCpk = it.find(CpiCpkEntity::class.java, cpiCpkKey)
                val cpkMetadata = it.find(CpkMetadataEntity::class.java, cpkKey)
                val cpkFile = it.find(CpkFileEntity::class.java, cpkKey)
                Triple(cpkMetadata, cpkFile, cpiCpk)
            }

            assertThat(cpkMetadata.cpkFileChecksum).isEqualTo(expectedCpkFileChecksum ?: cpk.csum)
            assertThat(cpkFile.fileChecksum).isEqualTo(expectedCpkFileChecksum ?: cpk.csum)

            assertThat(cpkMetadata.entityVersion)
                .withFailMessage("CpkMetadataEntity.entityVersion expected $expectedMetadataEntityVersion but was ${cpkMetadata.entityVersion}.")
                .isEqualTo(expectedMetadataEntityVersion)
            assertThat(cpkFile.entityVersion)
                .withFailMessage("CpkFileEntity.entityVersion expected $expectedFileEntityVersion but was ${cpkFile.entityVersion}.")
                .isEqualTo(expectedFileEntityVersion)
            assertThat(cpiCpk.entityVersion)
                .withFailMessage("CpiCpkEntity.entityVersion expected $expectedCpiCpkEntityVersion but was ${cpiCpk.entityVersion}.")
                .isEqualTo(expectedCpiCpkEntityVersion)
        }
    }
}

