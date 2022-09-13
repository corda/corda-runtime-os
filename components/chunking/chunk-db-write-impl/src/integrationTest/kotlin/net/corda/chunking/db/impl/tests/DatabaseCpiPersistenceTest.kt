package net.corda.chunking.db.impl.tests

import com.google.common.jimfs.Jimfs
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Random
import java.util.UUID
import javax.persistence.PersistenceException
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.db.impl.persistence.PersistenceUtils.toCpkKey
import net.corda.chunking.db.impl.persistence.database.DatabaseCpiPersistence
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.*
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

val Cpk.csum: String get() = metadata.fileChecksum.toString()

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
    private val mockChangeLogContent = "lorum ipsum"
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
        name: String,
        fileChecksum: SecureHash = newRandomSecureHash(),
        cpkSignerSummaryHash: SecureHash? = newRandomSecureHash()
    ) = mock<Cpk>().also { cpk ->
        val cpkId = CpkIdentifier(
            name = name,
            version = "cpk-version",
            signerSummaryHash = cpkSignerSummaryHash
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
            dependencies = emptyList(),
            cordappManifest = cordappManifest,
            type = CpkType.UNKNOWN,
            fileChecksum = fileChecksum,
            cordappCertificates = emptySet(),
            timestamp = Instant.now()
        )
        whenever(cpk.path).thenReturn(mockCpkContent.writeToPath())
        whenever(cpk.originalFileName).thenReturn(name)
        whenever(cpk.metadata).thenReturn(metadata)
    }

    private fun mockCpi(vararg cpks: Cpk, signerSummaryHash: SecureHash? = null): Cpi {
        // We need a random name here as the database primary key is (name, version, signerSummaryHash)
        // and we'd end up trying to insert the same mock cpi.
        val id = mock<CpiIdentifier> {
            whenever(it.name).thenReturn("test " + UUID.randomUUID().toString())
            whenever(it.version).thenReturn("1.0")
            whenever(it.signerSummaryHash).thenReturn(signerSummaryHash ?: SecureHash("SHA-256", ByteArray(12)))
        }

        return mockCpiWithId(cpks, id)
    }

    private fun mockCpiWithId(cpks: Array<out Cpk>, cpiId: CpiIdentifier): Cpi {
        val metadata = mock<CpiMetadata>().also {
            whenever(it.cpiId).thenReturn(cpiId)
            whenever(it.groupPolicy).thenReturn("{}")
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
        val (cpk) = makeCpks()
        val cpi = mockCpi(cpk)
        cpiPersistence.store(cpi)

        val cpkDataEntities: List<CpkFileEntity> = query("fileChecksum", cpk.csum)
        assertThat(cpkDataEntities.first().data).isEqualTo(mockCpkContent.toByteArray())
    }

    @Test
    fun `database cpi persistence can lookup persisted cpi by checksum`() {
        val (cpk) = makeCpks()
        assertThat(cpiPersistence.cpkExists(cpk.metadata.fileChecksum)).isFalse
        val cpi = mockCpi(cpk)
        cpiPersistence.store(cpi, "someFileName.cpi")
        assertThat(cpiPersistence.cpkExists(cpk.metadata.fileChecksum)).isTrue
    }

    private fun makeCpks(
        n: Int = 1,
        name: String? = null,
        fileChecksum: SecureHash? = null,
        signerSummaryHash: SecureHash? = null
    ): Array<Cpk> =
        (1..n).map {
            mockCpk(
                name ?: "${UUID.randomUUID()}.cpk",
                fileChecksum ?: newRandomSecureHash(),
                signerSummaryHash ?: newRandomSecureHash()
            )
        }.toTypedArray()

    private val random = Random(0)
    private fun newRandomSecureHash(): SecureHash {
        return SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
    }

    @Test
    fun `database cpi persistence can write multiple cpks into database`() {
        val cpi = mockCpi(cpks = makeCpks(3))
        cpiPersistence.store(cpi)
        assertThrows<PersistenceException> { cpiPersistence.store(cpi) }
    }

    @Test
    fun `database cpi persistence can write multiple CPIs with shared CPKs into database`() {
        val (sharedCpk, cpk1, cpk2) = makeCpks(3)

        val cpi1 = mockCpi(sharedCpk, cpk1)
        cpiPersistence.store(cpi1)

        val cpi2 = mockCpi(sharedCpk, cpk2)
        assertDoesNotThrow {
            cpiPersistence.store(cpi2)
        }

        // no updates to existing CPKs have occurred hence why all entity versions are 0
        findAndAssertCpks(listOf(Pair(cpi1, sharedCpk), Pair(cpi2, sharedCpk), Pair(cpi1, cpk1), Pair(cpi2, cpk2)))
    }

    @Test
    fun `database cpi persistence can force update a CPI`() {
        val (cpk1, updatedCpk) = makeCpks(2)
        val cpi = mockCpi(cpk1)
        val cpiFileName =
            "test${UUID.randomUUID()}.cpi" // control the filename so we have a fresh entity with known version number
        val cpiMetadataEntity = cpiPersistence.store(cpi, cpiFileName)

        assertThat(cpiMetadataEntity.entityVersion).isEqualTo(1)
        assertThat(cpiMetadataEntity.cpks.size).isEqualTo(1)
        assertThat(cpiMetadataEntity.cpks.first().entityVersion).isEqualTo(0)

        // make same assertions but after loading the entity again
        val initialLoadedCpi = loadCpiDirectFromDatabase(cpi)

        assertThat(initialLoadedCpi.entityVersion).isEqualTo(1)
        assertThat(initialLoadedCpi.cpks.size).isEqualTo(1)
        assertThat(initialLoadedCpi.cpks.first().entityVersion).isEqualTo(0)

        val updatedCpi = mockCpiWithId(arrayOf(cpk1, updatedCpk), cpi.metadata.cpiId)
        val returnedCpiMetadataEntity = cpiPersistence.store(updatedCpi, allowCpiUpdate = true)

        fun verifyDoubleCpi(
            cpiMetadata: CpiMetadataEntity,
            cpk1: Cpk,
            updatedCpk: Cpk
        ) {
            assertThat(cpiMetadata.cpks.size).isEqualTo(2)
            assertThat(cpiMetadata.entityVersion).isEqualTo(2) // craeted then modified, so on verison 2
            val firstReturnedCpk = cpiMetadata.cpks.first { it.cpkFileChecksum == cpk1.csum }
            val secondReturnedCpk = cpiMetadata.cpks.first { it.cpkFileChecksum == updatedCpk.csum }
            // JPA only increments entity version on the entities it is called on directly, not on embedded objects, and we insert
            // the CpiCpkEntity objects indirectly so they don't get modified, so are still at entityVersion=0
            assertThat(firstReturnedCpk.entityVersion).isEqualTo(1)
            assertThat(secondReturnedCpk.entityVersion).isEqualTo(0)
        }

        verifyDoubleCpi(returnedCpiMetadataEntity, cpk1, updatedCpk)
        val updatedLoadedCpi = loadCpiDirectFromDatabase(updatedCpi) // and check the same in the database
        verifyDoubleCpi(updatedLoadedCpi, cpk1, updatedCpk)
    }

    @Test
    fun `database cpi persistence can force update the same CPI`() {
        val cpiChecksum = newRandomSecureHash()
        val cpkChecksum = newRandomSecureHash()
        val cpk1 = mockCpk("${UUID.randomUUID()}.cpk", cpkChecksum)
        val cpi = mockCpi(cpk1)

        cpiPersistence.store(cpi, "test.cpi", cpiChecksum)

        val loadedCpi = loadCpiDirectFromDatabase(cpi)

        // adding cpk to cpi accounts for 1 modification
        assertThat(loadedCpi.entityVersion).isEqualTo(1)
        assertThat(loadedCpi.cpks.size).isEqualTo(1)
        assertThat(loadedCpi.cpks.first().entityVersion).isEqualTo(0)

        cpiPersistence.store(cpi, checksum = cpiChecksum, allowCpiUpdate = true)  // force update same CPI

        val updatedCpi = loadCpiDirectFromDatabase(cpi)

        assertThat(updatedCpi.insertTimestamp).isAfter(loadedCpi.insertTimestamp)
        // merging updated cpi accounts for 1 modification + modifying cpk
        assertThat(updatedCpi.entityVersion).isEqualTo(2)
        assertThat(updatedCpi.cpks.size).isEqualTo(1)
        assertThat(updatedCpi.cpks.first().entityVersion).isEqualTo(1)
    }

    private fun loadCpiDirectFromDatabase(cpi: Cpi) =
        entityManagerFactory.createEntityManager().transaction {
            it.find(
                CpiMetadataEntity::class.java, CpiMetadataEntityKey(
                    cpi.metadata.cpiId.name,
                    cpi.metadata.cpiId.version,
                    cpi.metadata.cpiId.signerSummaryHash.toString(),
                )
            )
        }!!

    @Test
    fun `CPKs are correct after persisting a CPI with already existing CPK`() {
        val (sharedCpk) = makeCpks()
        val cpi = mockCpi(sharedCpk)
        cpiPersistence.store(cpi, groupId = "group-a")
        val cpi2 = mockCpi(sharedCpk)
        cpiPersistence.store(cpi2, cpiFileName = "test2.cpi", groupId = "group-b")
        // no updates to existing CPKs have occurred hence why all entity versions are 0
        findAndAssertCpks(listOf(Pair(cpi, sharedCpk), Pair(cpi2, sharedCpk)))
    }

    @Test
    fun `CPKs are correct after updating a CPI by adding a new CPK`() {
        val (cpk, newCpk) = makeCpks(2)
        val cpi = mockCpi(cpk)
        cpiPersistence.store(cpi, groupId = "group-a")
        // a new cpi object, but with same ID and added new CPK
        val updatedCpi = mockCpiWithId(arrayOf(cpk, newCpk), cpi.metadata.cpiId)
        cpiPersistence.store(updatedCpi, groupId = "group-b", allowCpiUpdate = true)
        assertThat(cpi.metadata.cpiId).isEqualTo(updatedCpi.metadata.cpiId)
        // no updates to existing CPKs have occurred hence why all entity versions are 0. We are updating a CPI by adding a new CPK to it
        findAndAssertCpks(listOf(Pair(cpi, cpk)), expectedCpiCpkEntityVersion = 1)
        findAndAssertCpks(listOf(Pair(cpi, newCpk)))
    }

    @Test
    fun `CPK version is incremented when we update a CPK in a CPI`() {
        val (cpk) = makeCpks()
        val newChecksum = newRandomSecureHash()
        val updatedCpk = updatedCpk(cpk.metadata.cpkId, newChecksum)
        val cpi = mockCpi(cpk)
        cpiPersistence.store(cpi, groupId = "group-a")
        val updatedCpi = mockCpiWithId(arrayOf(updatedCpk), cpi.metadata.cpiId)  // a new cpi object, but with same ID
        cpiPersistence.store(updatedCpi, groupId = "group-b", allowCpiUpdate = true)
        assertThat(cpi.metadata.cpiId).isEqualTo(updatedCpi.metadata.cpiId)
        // we have updated an existing CPK with a new checksum (and data) hence why its entityVersion has incremented.
        findAndAssertCpks(
            listOf(Pair(cpi, cpk)),
            expectedCpkFileChecksum = newChecksum.toString(),
            expectedMetadataEntityVersion = 1,
            expectedFileEntityVersion = 1,
            expectedCpiCpkEntityVersion = 1
        )
    }

    @Test
    fun `CPK version is incremented when CpiCpkEntity has non-zero entityversion`() {
        val (cpk) = makeCpks()
        val cpi = mockCpi(cpk)
        cpiPersistence.store(cpi, groupId = "group-a")
        findAndAssertCpks(listOf(Pair(cpi, cpk)))

        // a new cpi object, but with same cpk
        val secondCpkChecksum = newRandomSecureHash()
        val updatedCpk = updatedCpk(cpk.metadata.cpkId, secondCpkChecksum)
        val updatedCpi = mockCpiWithId(arrayOf(updatedCpk), cpi.metadata.cpiId)

        cpiPersistence.store(updatedCpi, groupId = "group-b", allowCpiUpdate = true)

        // we have updated an existing CPK hence why the entity versions are incremented.
        findAndAssertCpks(
            listOf(Pair(cpi, cpk)),
            expectedCpkFileChecksum = updatedCpk.csum,
            expectedMetadataEntityVersion = 1,
            expectedFileEntityVersion = 1,
            expectedCpiCpkEntityVersion = 1
        )

        // a new cpi object, but with same cpk
        val thirdChecksum = newRandomSecureHash()
        val anotherUpdatedCpk = updatedCpk(cpk.metadata.cpkId, thirdChecksum)
        val anotherUpdatedCpi = mockCpiWithId(arrayOf(anotherUpdatedCpk), cpi.metadata.cpiId)

        cpiPersistence.store(anotherUpdatedCpi, groupId = "group-b", allowCpiUpdate = true)

        // We have updated the same CPK again hence why the entity versions are incremented again.
        findAndAssertCpks(
            listOf(Pair(cpi, cpk)),
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

        cpiPersistence.store(cpi, "$testId.cpi", groupId = "group-a")
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


    private fun makeChangeLogs(cpks: Array<Cpk>) = cpks.map {
        CpkDbChangeLogEntity(
            CpkDbChangeLogKey(
                it.metadata.cpkId.name,
                it.metadata.cpkId.version,
                it.metadata.cpkId.signerSummaryHash.toString(),
                "resources/db.changelog-master.xml"
            ),
            newRandomSecureHash().toString(),
            mockChangeLogContent
        )
    }

    @Test
    fun `persist changelog writes data and can be read back`() {
        val (cpk) = makeCpks()
        val cpi = mockCpi(cpk)
        cpiPersistence.store(cpi, cpkDbChangeLogEntities = makeChangeLogs(arrayOf(cpk)))

        val changeLogsRetrieved = query<CpkDbChangeLogEntity, String>("cpk_name", cpk.metadata.cpkId.name)

        assertThat(changeLogsRetrieved.size).isGreaterThanOrEqualTo(1)
        assertThat(changeLogsRetrieved.first().content).isEqualTo(mockChangeLogContent)
    }

    @Test
    fun `persist multiple changelogs writes data and can be read back`() {
        val cpks = makeCpks(5)
        val cpi = mockCpi(cpks = cpks)
        cpiPersistence.store(cpi, cpkDbChangeLogEntities = makeChangeLogs(cpks))

        val changeLogsRetrieved = query<CpkDbChangeLogEntity, String>("content", mockChangeLogContent)

        assertThat(changeLogsRetrieved.size).isGreaterThanOrEqualTo(5)
        assertThat(changeLogsRetrieved.first().content).isEqualTo(mockChangeLogContent)
    }

    @Test
    fun `version number of changelog increases when changelogs are updated`() {
        val signerSummaryHash = newRandomSecureHash()
        val name = "${UUID.randomUUID()}.cpk"
        val cpks = makeCpks(5, name = name, signerSummaryHash = signerSummaryHash)
        for ((i, cpk) in cpks.withIndex()) {
            val cpi = mockCpi(cpk, signerSummaryHash = signerSummaryHash)
            cpiPersistence.store(cpi, cpkDbChangeLogEntities = makeChangeLogs(cpks = arrayOf(cpk)))
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
}

