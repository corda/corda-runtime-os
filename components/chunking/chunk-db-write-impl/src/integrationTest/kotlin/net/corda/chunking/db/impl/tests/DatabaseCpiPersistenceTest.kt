package net.corda.chunking.db.impl.tests

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.jimfs.Jimfs
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.db.impl.persistence.database.DatabaseCpiPersistence
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.KeyValuePairList
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiCpkIdentifier
import net.corda.libs.cpi.datamodel.CpiCpkMetadata
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.libs.cpi.datamodel.repository.factory.CpiCpkRepositoryFactory
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
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.MembershipEntities
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.impl.network.writer.staticnetwork.NetworkInfoDBWriterImpl
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_STATIC_MGM
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.STATIC_NETWORK
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.MGM_INFO
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.MemberGroupPolicy
import net.corda.membership.network.writer.NetworkInfoWriter
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.dsl.entities.cpx.cpkDbChangeLog
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
import java.security.PublicKey
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
        ChunkingEntities.classes.toList() + CpiEntities.classes.toList() + MembershipEntities.clusterClasses.toList(),
        emConfig
    )
    private val platformInfoProvider: PlatformInfoProvider = mock {
        on { activePlatformVersion } doReturn 12345
        on { localWorkerSoftwareVersion } doReturn "5.0.0"
    }
    private val mgmPubKeyStr = "PUB-KEY"
    private val mgmPubKeyEncoded = mgmPubKeyStr.toByteArray()
    private val mgmPubKey: PublicKey = mock {
        on { encoded } doReturn mgmPubKeyEncoded
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { encodeAsString(mgmPubKey) } doReturn mgmPubKeyStr
        on { decodePublicKey(any<ByteArray>()) } doReturn mgmPubKey
    }
    private val mockGroupPolicy = mock<MemberGroupPolicy> {
        on { protocolParameters } doReturn mock()
    }
    private val groupPolicyParser = mock<GroupPolicyParser> {
        on { parseMember(any()) } doReturn mockGroupPolicy
    }
    private val serializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn "serialized-bytes".toByteArray()
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn serializer
    }
    private val networkInfoWriter: NetworkInfoWriter = NetworkInfoDBWriterImpl(
        platformInfoProvider,
        keyEncodingService,
        groupPolicyParser,
        cordaAvroSerializationFactory
    )

    private val cpiCpkRepositoryFactory = CpiCpkRepositoryFactory()
    private val cpkDbChangeLogRepository = cpiCpkRepositoryFactory.createCpkDbChangeLogRepository()
    private val cpkDbChangeLogAuditRepository = cpiCpkRepositoryFactory.createCpkDbChangeLogAuditRepository()
    private val cpkFileRepository = cpiCpkRepositoryFactory.createCpkFileRepository()
    private val cpiMetadataRepository = cpiCpkRepositoryFactory.createCpiMetadataRepository()
    private val cpiCpkRepository = cpiCpkRepositoryFactory.createCpiCpkRepository()
    private val cpkRepository = cpiCpkRepositoryFactory.createCpkRepository()

    private val cpiPersistence = DatabaseCpiPersistence(
        entityManagerFactory,
        networkInfoWriter,
        cpiMetadataRepository,
        cpkDbChangeLogRepository,
        cpkDbChangeLogAuditRepository,
        cpkFileRepository,
        mock())

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

    private fun updatedCpk(newFileChecksum: SecureHash = newRandomSecureHash(), cpkId: CpkIdentifier) =
        mockCpk(newFileChecksum, cpkId.name, cpkId.version, cpkId.signerSummaryHash)

    private fun mockCpk(
        fileChecksum: SecureHash = newRandomSecureHash(),
        name: String = UUID.randomUUID().toString(),
        version: String = "cpk-version",
        signerSummaryHash: SecureHash = newRandomSecureHash()
    ) = mock<Cpk>().also { cpk ->
        val cpkId = CpkIdentifier(
            name = name,
            version = version,
            signerSummaryHash = signerSummaryHash
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
            fileChecksum = fileChecksum,
            cordappCertificates = emptySet(),
            timestamp = Instant.now(),
            externalChannelsConfig = "{}"
        )
        whenever(cpk.path).thenReturn(mockCpkContent.writeToPath())
        whenever(cpk.originalFileName).thenReturn("$name.cpk")
        whenever(cpk.metadata).thenReturn(metadata)
    }

    @Suppress("LongParameterList")
    private fun mockCpi(
        vararg cpks: Cpk,
        signerSummaryHash: SecureHash = SecureHashImpl("SHA-256", ByteArray(12)),
        name: String = UUID.randomUUID().toString(),
        version: String = "1.0",
        fileChecksum: SecureHash? = newRandomSecureHash(),
        groupPolicy: String = "{}"
    ): Cpi {
        // We need a random name here as the database primary key is (name, version, signerSummaryHash)
        // and we'd end up trying to insert the same mock cpi.
        val id = mock<CpiIdentifier> {
            whenever(it.name).thenReturn(name)
            whenever(it.version).thenReturn(version)
            whenever(it.signerSummaryHash).thenReturn(signerSummaryHash)
        }

        return mockCpiWithId(cpks.toList(), id, fileChecksum, groupPolicy)
    }

    private fun mockCpiWithId(
        cpks: List<Cpk>,
        cpiId: CpiIdentifier,
        fileChecksum: SecureHash? = newRandomSecureHash(),
        groupPolicy: String = "{}"
    ): Cpi {
        val metadata = mock<CpiMetadata>().also {
            whenever(it.cpiId).thenReturn(cpiId)
            whenever(it.groupPolicy).thenReturn(groupPolicy)
            whenever(it.fileChecksum).thenReturn(fileChecksum)
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
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)

        entityManagerFactory.createEntityManager().transaction {
            val cpkFile: CpkFile = cpkFileRepository.findById(it, cpi.cpks.first().metadata.fileChecksum)
            assertThat(cpkFile.data).isEqualTo(mockCpkContent.toByteArray())
        }
    }

    @Test
    fun `database cpi persistence can lookup persisted cpi by checksum`() {
        val cpk = mockCpk()
        assertThat(cpiPersistence.cpkExists(cpk.metadata.fileChecksum)).isFalse
        val cpi = mockCpi(cpk)
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, "someFileName.cpi")
        assertThat(cpiPersistence.cpkExists(cpk.metadata.fileChecksum)).isTrue
    }

    @Test
    fun `database cpi persistence throws when persisting the same CPI twice`() {
        val cpi = mockCpi(mockCpk(), mockCpk(), mockCpk())
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)
        assertThrows<PersistenceException> { cpiPersistence.persistMetadataAndCpksWithDefaults(cpi) }
    }

    @Test
    fun `database cpi persistence can write multiple CPIs with shared CPKs into database`() {
        val sharedCpk = mockCpk()
        val cpk1 = mockCpk()
        val cpk2 = mockCpk()

        val cpi1 = mockCpi(sharedCpk, cpk1)
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi1)

        val cpi2 = mockCpi(sharedCpk, cpk2)
        assertDoesNotThrow {
            cpiPersistence.persistMetadataAndCpksWithDefaults(cpi2)
        }

        // no updates to existing CPKs have occurred hence why all entity versions are 0
        findAndAssertCpks(listOf(Pair(cpi1, sharedCpk), Pair(cpi2, sharedCpk), Pair(cpi1, cpk1), Pair(cpi2, cpk2)))
    }

    @Test
    fun `database cpi persistence can force update a CPI`() {
        val cpk1 = mockCpk()
        val cpi = mockCpi(cpk1)
        val cpiFileName = "test${UUID.randomUUID()}.cpi"

        // first of all, persist the original CPI along with its associated CPKs and a CpkDbChangeLog
        val groupId = "group-a"
        cpiPersistence.persistMetadataAndCpks(
            cpi,
            cpiFileName,
            newRandomSecureHash(),
            UUID.randomUUID().toString(),
            groupId,
            listOf(
                cpkDbChangeLog {
                    fileChecksum(cpk1.metadata.fileChecksum)
                    filePath(cpk1.path.toString())
                }
            )
        )

        val persistedCpi = loadCpiDirectFromDatabase(cpi.metadata.cpiId)
        val persistedCpiCpk = loadCpiCpkDirectFromDatabase(
            CpiCpkIdentifier(
                cpi.metadata.cpiId.name,
                cpi.metadata.cpiId.version,
                cpi.metadata.cpiId.signerSummaryHash,
                persistedCpi.cpksMetadata.first().fileChecksum
            )
        )

        // We have persisted a CPK with this CPI, this counts as a version increment on the owning entity, therefore entity version = 1.
        assertThat(persistedCpi.version).isEqualTo(1)
        assertThat(persistedCpi.cpksMetadata.size).isEqualTo(1)
        // The CPK which was merged will have entity version 0.
        assertThat(persistedCpiCpk.entityVersion).isEqualTo(0)

        val cpk2 = mockCpk()
        val updatedCpi = mockCpiWithId(listOf(cpk1, cpk2), cpi.metadata.cpiId)

        // simulate a force update to CPI, including adding two change logs
        cpiPersistence.updateMetadataAndCpks(
            updatedCpi,
            cpiFileName,
            newRandomSecureHash(),
            UUID.randomUUID().toString(),
            groupId,
            listOf(
                cpkDbChangeLog {
                    fileChecksum(cpk1.metadata.fileChecksum)
                    filePath(cpk1.path.toString())
                },
                cpkDbChangeLog {
                    fileChecksum(cpk2.metadata.fileChecksum)
                    filePath(cpk2.path.toString())
                }
            )
        )

        val forceUploadedCpi = loadCpiDirectFromDatabase(updatedCpi.metadata.cpiId)
        val forceUploadedCpiCpk = loadCpiCpkDirectFromDatabase(
            CpiCpkIdentifier(
                updatedCpi.metadata.cpiId.name,
                updatedCpi.metadata.cpiId.version,
                updatedCpi.metadata.cpiId.signerSummaryHash,
                cpk1.metadata.fileChecksum
            )
        )

        val forceUploadedCpiCpk2 = loadCpiCpkDirectFromDatabase(
            CpiCpkIdentifier(
                updatedCpi.metadata.cpiId.name,
                updatedCpi.metadata.cpiId.version,
                updatedCpi.metadata.cpiId.signerSummaryHash,
                cpk2.metadata.fileChecksum
            )
        )

        // optimistic force increment + calling merge on this entity has incremented by 2
        assertThat(forceUploadedCpi.version).isEqualTo(3)
        assertThat(forceUploadedCpi.cpksMetadata.size).isEqualTo(2)

        // cpk1 has incremented because we called merge on the CPI with this entity already existing in the set.
        assertThat(forceUploadedCpiCpk.entityVersion).isEqualTo(0) // /CpiCpkMetadata
        val (forceUploadedCpkEntityVersion1, _) = findCpkMetadata(cpk1.metadata.fileChecksum)
        assertThat(forceUploadedCpkEntityVersion1).isEqualTo(0) //CpkMetadata

        assertThat(forceUploadedCpiCpk2.entityVersion).isEqualTo(0)
        val (forceUploadedCpkEntityVersion2, _) = findCpkMetadata(cpk2.metadata.fileChecksum)
        assertThat(forceUploadedCpkEntityVersion2).isEqualTo(0) //CpkMetadata

        assertChangeLogPersistedWithCpi(cpk1)
        assertChangeLogPersistedWithCpi(cpk2)
    }

    private fun assertChangeLogPersistedWithCpi(cpk: Cpk) {
        val dbChangeLogAsList =
            loadCpkDbChangeLog(CpkDbChangeLogIdentifier(cpk.metadata.fileChecksum, cpk.path.toString()))
        assertThat(dbChangeLogAsList).isNotNull
        assertThat(dbChangeLogAsList.id.cpkFileChecksum).isEqualTo(cpk.metadata.fileChecksum)
    }

    private fun loadCpkDbChangeLog(fileChecksum: SecureHash): List<CpkDbChangeLog> {
        return entityManagerFactory.createEntityManager().transaction { em ->
            cpkDbChangeLogRepository.findByFileChecksum(em, setOf(fileChecksum.toString()))
        }
    }

    private fun loadCpkDbChangeLog(changeLogIdentifier: CpkDbChangeLogIdentifier): CpkDbChangeLog {
        return entityManagerFactory.createEntityManager().transaction { em ->
            cpkDbChangeLogRepository.findById(em, changeLogIdentifier)
        }
    }

    private fun loadCpkDbChangeLog(content: String): List<CpkDbChangeLog> {
        return entityManagerFactory.createEntityManager().transaction { em ->
            cpkDbChangeLogRepository.findByContent(em, content)
        }
    }

    @Test
    fun `database cpi persistence can force update the same CPI`() {
        val cpiChecksum = newRandomSecureHash()
        val cpi = mockCpi(mockCpk())

        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, "test.cpi", cpiChecksum)

        val loadedCpi = loadCpiDirectFromDatabase(cpi.metadata.cpiId)
        val loadedCpiCpk = loadCpiCpkDirectFromDatabase(
            CpiCpkIdentifier(
                cpi.metadata.cpiId.name,
                cpi.metadata.cpiId.version,
                cpi.metadata.cpiId.signerSummaryHash,
                loadedCpi.cpksMetadata.first().fileChecksum
            )
        )

        assertThat(loadedCpi.version).isEqualTo(1) //CpiMetadata
        assertThat(loadedCpi.cpksMetadata.size).isEqualTo(1)
        assertThat(loadedCpiCpk.entityVersion).isEqualTo(0)

        cpiPersistence.updateMetadataAndCpksWithDefaults(
            cpi,
            cpiFileChecksum = cpiChecksum
        )  // force update same CPI

        val updatedCpi = loadCpiDirectFromDatabase(cpi.metadata.cpiId)
        val updatedCpiCpk = loadCpiCpkDirectFromDatabase(
            CpiCpkIdentifier(
                cpi.metadata.cpiId.name,
                cpi.metadata.cpiId.version,
                cpi.metadata.cpiId.signerSummaryHash,
                updatedCpi.cpksMetadata.first().fileChecksum
            )
        )

        assertThat(updatedCpi.timestamp).isAfter(loadedCpi.timestamp)
        // merging updated cpi accounts for 1 modification, find with optimistic force increment accounts for the other modification
        assertThat(updatedCpi.version).isEqualTo(3)
        assertThat(updatedCpi.cpksMetadata.size).isEqualTo(1)
        assertThat(updatedCpiCpk.entityVersion).isEqualTo(0)
    }

    @Test
    fun `CPKs are correct after persisting a CPI with already existing CPK`() {
        val sharedCpk = mockCpk()
        val cpi = mockCpi(sharedCpk)
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, groupId = "group-a")
        val cpi2 = mockCpi(sharedCpk)
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi2, cpiFileName = "test2.cpi", groupId = "group-b")
        // no updates to existing CPKs have occurred hence why all entity versions are 0
        findAndAssertCpks(listOf(Pair(cpi, sharedCpk), Pair(cpi2, sharedCpk)))
    }

    @Test
    fun `CPKs are correct after updating a CPI by adding a new CPK`() {
        val cpk1 = mockCpk()
        val cpi = mockCpi(cpk1)
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, groupId = "group-a")
        // a new cpi object, but with same ID and added new CPK
        val cpk2 = mockCpk()
        val updatedCpi = mockCpiWithId(listOf(cpk1, cpk2), cpi.metadata.cpiId)
        cpiPersistence.updateMetadataAndCpksWithDefaults(updatedCpi, groupId = "group-b")
        assertThat(cpi.metadata.cpiId).isEqualTo(updatedCpi.metadata.cpiId)

        findAndAssertCpks(listOf(Pair(cpi, cpk1)))
        findAndAssertCpks(listOf(Pair(cpi, cpk2)))
    }

    @Test
    fun `update CPI replacing its CPK with a new one with new file checksum`() {
        val cpk = mockCpk()
        val cpi = mockCpi(cpk)
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, groupId = "group-a")

        val newChecksum = newRandomSecureHash()
        val updatedCpk = updatedCpk(newChecksum, cpk.metadata.cpkId)
        val updatedCpi = mockCpiWithId(listOf(updatedCpk), cpi.metadata.cpiId)  // a new cpi object, but with same ID
        cpiPersistence.updateMetadataAndCpksWithDefaults(updatedCpi, groupId = "group-b")

        assertThat(cpi.metadata.cpiId).isEqualTo(updatedCpi.metadata.cpiId)
        assertCpkIsNotAssociatedWithCpi(cpi, cpk)
        findAndAssertCpks(
            listOf(Pair(cpi, updatedCpk)),
            expectedCpkFileChecksum = newChecksum
        )
    }

    @Test
    fun `multiple CPKs with the same name, version, signerSummaryHash but different checksum are allowed in a CPI`() {
        val rand = UUID.randomUUID().toString()
        val cpkName = "name_$rand"
        val cpkVersion = "version_$rand"
        val cpkSignerSummaryHash = newRandomSecureHash()
        val cpkFileChecksum1 = newRandomSecureHash()
        val cpkFileChecksum2 = newRandomSecureHash()

        val cpk1 = mockCpk(cpkFileChecksum1, cpkName, cpkVersion, cpkSignerSummaryHash)
        val cpk2 = mockCpk(cpkFileChecksum2, cpkName, cpkVersion, cpkSignerSummaryHash)
        val cpi = mockCpi(cpk1, cpk2)

        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, groupId = "group-a")

        findAndAssertCpks(
            listOf(Pair(cpi, cpk1)),
            expectedCpkFileChecksum = cpkFileChecksum1
        )
        findAndAssertCpks(
            listOf(Pair(cpi, cpk2)),
            expectedCpkFileChecksum = cpkFileChecksum2
        )
    }

    @Test
    fun `updating CPI multiple times with new CPKs`() {
        val cpk1 = mockCpk()
        val cpi = mockCpi(cpk1)
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, groupId = "group-a")

        findAndAssertCpks(listOf(Pair(cpi, cpk1)))

        val secondCpkChecksum = newRandomSecureHash()
        val updatedCpk = updatedCpk(secondCpkChecksum, cpi.cpks.first().metadata.cpkId)
        val updatedCpi = mockCpiWithId(listOf(updatedCpk), cpi.metadata.cpiId)

        cpiPersistence.updateMetadataAndCpksWithDefaults(updatedCpi, groupId = "group-b")

        assertCpkIsNotAssociatedWithCpi(cpi, cpk1)
        findAndAssertCpks(
            listOf(Pair(cpi, updatedCpk)),
            expectedCpkFileChecksum = updatedCpk.metadata.fileChecksum
        )

        // a new cpi object, with a new cpk file checksum
        val thirdChecksum = newRandomSecureHash()
        val anotherUpdatedCpk = updatedCpk(thirdChecksum, cpi.cpks.first().metadata.cpkId)
        val anotherUpdatedCpi = mockCpiWithId(listOf(anotherUpdatedCpk), cpi.metadata.cpiId)

        cpiPersistence.updateMetadataAndCpksWithDefaults(anotherUpdatedCpi, groupId = "group-b")

        assertCpkIsNotAssociatedWithCpi(cpi, updatedCpk)
        findAndAssertCpks(
            listOf(Pair(cpi, anotherUpdatedCpk)),
            expectedCpkFileChecksum = thirdChecksum,
        )
    }

    @Test
    fun `persist changelog writes data and can be read back`() {
        val cpi = mockCpi(mockCpk())
        cpiPersistence.persistMetadataAndCpksWithDefaults(
            cpi,
            cpkDbChangeLog = genChangeLogs(arrayOf(cpi.cpks.first()))
        )

        val changeLogsRetrieved = loadCpkDbChangeLog(cpi.cpks.first().metadata.fileChecksum)

        assertThat(changeLogsRetrieved.size).isGreaterThanOrEqualTo(1)
        assertThat(changeLogsRetrieved.first().content).isEqualTo(mockChangeLogContent)
    }

    @Test
    fun `persist multiple changelogs writes data and can be read back`() {
        val cpi = mockCpi(mockCpk(), mockCpk(), mockCpk(), mockCpk(), mockCpk())
        cpiPersistence.persistMetadataAndCpksWithDefaults(cpi, cpkDbChangeLog = genChangeLogs(cpi.cpks.toTypedArray()))

        val changeLogsRetrieved = loadCpkDbChangeLog(mockChangeLogContent)

        assertThat(changeLogsRetrieved.size).isGreaterThanOrEqualTo(5)
        assertThat(changeLogsRetrieved.first().content).isEqualTo(mockChangeLogContent)
    }

    @Test
    fun `after force uploading CPI with new CPK, we don't get changelogs from old CPKs`() {
        val cpk1 = mockCpk()
        val cpiWithChangelogs = mockCpi(cpk1)

        val cpiMetadata = cpiPersistence.persistMetadataAndCpksWithDefaults(
            cpiWithChangelogs,
            cpkDbChangeLog = listOf(
                cpkDbChangeLog {
                    fileChecksum(cpk1.metadata.fileChecksum)
                    filePath(cpk1.path.toString())
                }
            )
        )

        val changelogsWith = findChangelogs(cpiMetadata.cpiId)
        assertThat(changelogsWith.size).isEqualTo(1)

        val updatedCpi = mockCpiWithId(listOf(mockCpk()), cpiWithChangelogs.metadata.cpiId)

        // no change sets in this CPK
        val updateCpiMetadata = cpiPersistence.updateMetadataAndCpksWithDefaults(updatedCpi)

        val changelogsWithout = findChangelogs(updateCpiMetadata.cpiId)
        assertThat(changelogsWithout.size).isEqualTo(0)
    }

    @Test
    fun `cannot store multiple versions of the same CPI name in the same group`() {
        val name = UUID.randomUUID().toString()
        val cpiV1 = mockCpi(mockCpk(), name = name, version = "v1")
        val cpiV2 = mockCpi(mockCpk(), name = name, version = "v2")
        val cpiEntityV1 = cpiPersistence.persistMetadataAndCpksWithDefaults(cpiV1)
        val cpiEntityV2 = cpiPersistence.persistMetadataAndCpksWithDefaults(cpiV2)
        assertThat(cpiEntityV1.cpiId.name).isEqualTo(name)
        assertThat(cpiEntityV1.cpiId.version).isEqualTo("v1")
        assertThat(cpiEntityV1.cpksMetadata).hasSize(1)
        assertThat(cpiEntityV2.cpiId.name).isEqualTo(name)
        assertThat(cpiEntityV2.cpiId.version).isEqualTo("v2")
        assertThat(cpiEntityV2.cpksMetadata).hasSize(1)
    }

    @Test
    fun `uploading CPIs with new CPKs adds changelog and audit entries even with the same file path for each CPK`() {
        val cpk1 = mockCpk()
        val cpi = mockCpi(cpk1)
        val rand1 = UUID.randomUUID().toString()
        val filePath = "path_1_$rand1.xml"
        val cpiMetadata = cpiPersistence.persistMetadataAndCpksWithDefaults(
            cpi,
            cpkDbChangeLog = listOf(
                cpkDbChangeLog {
                    fileChecksum(cpk1.metadata.fileChecksum)
                    filePath(filePath)
                }
            )
        )

        val changelogs = findChangelogs(cpiMetadata.cpiId)
        val changelogAudits = findAuditLogs(listOf(cpk1.metadata.fileChecksum))
        assertThat(changelogs.size).isEqualTo(1)
        assertThat(changelogAudits.size).isEqualTo(1)

        val cpk2 = mockCpk()
        val updatedCpi = mockCpiWithId(listOf(cpk1, cpk2), cpi.metadata.cpiId)

        val updateCpiMetadata = cpiPersistence.updateMetadataAndCpksWithDefaults(
            updatedCpi,
            cpkDbChangeLog = listOf(
                cpkDbChangeLog {
                    fileChecksum(cpk1.metadata.fileChecksum)
                    filePath(filePath)
                },
                cpkDbChangeLog {
                    fileChecksum(cpk2.metadata.fileChecksum)
                    filePath(filePath)
                }
            )
        )
        val updatedChangelogs = findChangelogs(updateCpiMetadata.cpiId)
        val updatedChangelogAudits = findAuditLogs(listOf(cpk1.metadata.fileChecksum, cpk2.metadata.fileChecksum))

        assertThat(updatedChangelogs.size)
            .withFailMessage("Expecting 2 changelogs, one for each CPK with unique file path associated")
            .isEqualTo(2)
        assertThat(updatedChangelogAudits.size)
            .withFailMessage("Expecting the following changelog audit records: Initial cpi: cpk1, updatedCpi: cpk1, cpk2")
            .isEqualTo(3)
    }

    @Test
    fun `force upload adds multiple changelog audit entry for multiple changesets with different filePaths`() {
        val cpk = mockCpk()
        val cpi = mockCpi(cpk)
        val cpk1FileChecksum = cpk.metadata.fileChecksum
        val cpiMetadata = cpiPersistence.persistMetadataAndCpksWithDefaults(
            cpi,
            cpkDbChangeLog = listOf(
                cpkDbChangeLog {
                    fileChecksum(cpk1FileChecksum)
                }
            )
        )

        val changelogs = findChangelogs(cpiMetadata.cpiId)
        val changelogAudits = findAuditLogs(listOf(cpk.metadata.fileChecksum))
        assertThat(changelogs.size).isEqualTo(1)
        assertThat(changelogAudits.size).isEqualTo(1)

        val cpk2 = mockCpk()
        val updatedCpi = mockCpiWithId(listOf(cpk2), cpi.metadata.cpiId)
        val cpk2FileChecksum = cpk2.metadata.fileChecksum

        val updateCpiMetadata = cpiPersistence.updateMetadataAndCpksWithDefaults(
            updatedCpi,
            cpkDbChangeLog = listOf(
                cpkDbChangeLog {
                    fileChecksum(cpk2FileChecksum)
                    // (randomized file paths)
                },
                cpkDbChangeLog {
                    fileChecksum(cpk2FileChecksum)
                },
            )
        )

        val updatedChangelogs = findChangelogs(updateCpiMetadata.cpiId)
        assertThat(updatedChangelogs.size).isEqualTo(2)

        val updatedChangelogAudits = findAuditLogs(listOf(cpk1FileChecksum, cpk2FileChecksum))
        assertThat(updatedChangelogAudits.size).isEqualTo(3)
    }

    /**
     * Tests verifying the static network information persisted during CPI upload.
     */
    @Nested
    inner class StaticNetworkInfoTests {
        private val mapper = ObjectMapper()
        private val groupId = UUID.randomUUID().toString()
        private val minimumStaticNetworkGroupPolicy = """
            {
                "$GROUP_ID": "$groupId",
                "$PROTOCOL_PARAMETERS": {
                    "$STATIC_NETWORK": {}
                }
            }
            """.trimIndent()

        private val nonStaticNetworkGroupPolicy = """
            {
                "$GROUP_ID": "$groupId",
                "$PROTOCOL_PARAMETERS": {}
            }
            """.trimIndent()

        @Test
        fun `database cpi persistence persists static network data if group policy is for a static network`() {
            whenever(mockGroupPolicy.groupId).doReturn(groupId)
            val cpi = mockCpi(mockCpk(), groupPolicy = minimumStaticNetworkGroupPolicy)

            cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)

            val persistedEntity = entityManagerFactory.createEntityManager().transaction {
                it.find(StaticNetworkInfoEntity::class.java, groupId)
            }
            assertThat(persistedEntity).isNotNull
            assertThat(persistedEntity.groupId).isEqualTo(groupId)
        }

        @Test
        fun `database cpi persistence does not persist static network data if group policy is not for a static network`() {
            val cpi = mockCpi(mockCpk(), groupPolicy = nonStaticNetworkGroupPolicy)

            cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)

            val persistedEntity = entityManagerFactory.createEntityManager().transaction {
                it.find(StaticNetworkInfoEntity::class.java, groupId)
            }
            assertThat(persistedEntity).isNull()

        }

        @Test
        fun `database cpi persistence add static network MGM to group policy if it is for a static network`() {
            whenever(mockGroupPolicy.groupId).doReturn(groupId)
            val cpi = mockCpi(mockCpk(), groupPolicy = minimumStaticNetworkGroupPolicy)

            cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)

            val originalGroupPolicy = mapper.readTree(minimumStaticNetworkGroupPolicy)
            assertThat(originalGroupPolicy.has(MGM_INFO)).isFalse

            val persistedGroupPolicy = mapper.readTree(findCpiMetadata(cpi.metadata.cpiId).groupPolicy)
            assertThat(persistedGroupPolicy.has(MGM_INFO)).isTrue

            val mgmInfo = mapper.convertValue(
                persistedGroupPolicy[MGM_INFO],
                object : TypeReference<Map<String, String>>() {}
            )
            assertThat(mgmInfo[MemberInfoExtension.GROUP_ID]).isEqualTo(groupId)
            assertThat(mgmInfo[IS_STATIC_MGM]).isEqualTo("true")
        }

        @Test
        fun `database cpi persistence does not add static network MGM to group policy if it is not for a static network`() {
            val cpi = mockCpi(mockCpk(), groupPolicy = nonStaticNetworkGroupPolicy)

            cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)

            val originalGroupPolicy = mapper.readTree(nonStaticNetworkGroupPolicy)
            assertThat(originalGroupPolicy.has(MGM_INFO)).isFalse

            val persistedGroupPolicy = mapper.readTree(findCpiMetadata(cpi.metadata.cpiId).groupPolicy)
            assertThat(persistedGroupPolicy.has(MGM_INFO)).isFalse
        }

        @Test
        fun `database cpi persistence operation returns the static network persisted group policy in the returned entity`() {
            whenever(mockGroupPolicy.groupId).doReturn(groupId)
            val cpi = mockCpi(mockCpk(), groupPolicy = minimumStaticNetworkGroupPolicy)

            val result = cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)
            assertThat(result.groupPolicy).isNotEqualTo(minimumStaticNetworkGroupPolicy)
            assertThat(result.groupPolicy).isEqualTo(findCpiMetadata(cpi.metadata.cpiId).groupPolicy)
        }

        @Test
        fun `database cpi persistence operation returns the non static network persisted group policy in the returned entity`() {
            val cpi = mockCpi(mockCpk(), groupPolicy = nonStaticNetworkGroupPolicy)

            val result = cpiPersistence.persistMetadataAndCpksWithDefaults(cpi)
            assertThat(result.groupPolicy).isEqualTo(nonStaticNetworkGroupPolicy)
            assertThat(result.groupPolicy).isEqualTo(findCpiMetadata(cpi.metadata.cpiId).groupPolicy)
        }
    }

    private fun findChangelogs(cpiId: CpiIdentifier) = entityManagerFactory.createEntityManager().transaction {
        cpkDbChangeLogRepository.findByCpiId(it, cpiId)
    }

    private fun findAuditLogs(cpkFileChecksums: Collection<SecureHash>) =
        entityManagerFactory.createEntityManager().transaction {
            cpkDbChangeLogAuditRepository.findByFileChecksums(it, cpkFileChecksums)
        }

    private fun assertCpkIsNotAssociatedWithCpi(cpi: Cpi, cpk: Cpk) {
        entityManagerFactory.createEntityManager().transaction { em ->
            assertThat(
                cpiCpkRepository.exist(
                    em,
                    CpiCpkIdentifier(
                        cpi.metadata.cpiId.name,
                        cpi.metadata.cpiId.version,
                        cpi.metadata.cpiId.signerSummaryHash,
                        cpk.metadata.fileChecksum
                    )
                )
            ).isFalse()
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

    private fun newRandomSecureHash(): SecureHash {
        val random = Random()
        return SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    }

    private fun genChangeLogs(
        cpks: Array<Cpk>,
        changeLogs: List<String> = listOf(mockChangeLogContent)
    ): List<CpkDbChangeLog> = cpks.flatMap { cpk ->
        changeLogs.map { changeLog ->
            CpkDbChangeLog(
                CpkDbChangeLogIdentifier(cpk.metadata.fileChecksum, "resources/$changeLog"),
                changeLog
            )
        }
    }

    private fun findCpiMetadata(cpiId: CpiIdentifier): CpiMetadata =
        entityManagerFactory.createEntityManager().transaction { em ->
            cpiMetadataRepository.findById(em, cpiId)
        }!!

    private fun loadCpiDirectFromDatabase(cpiId: CpiIdentifier): CpiMetadata =
        entityManagerFactory.createEntityManager().transaction { em ->
            cpiMetadataRepository.findById(em, cpiId)
        }!!

    private fun loadCpiCpkDirectFromDatabase(cpiCpkId: CpiCpkIdentifier): CpiCpkMetadata =
        entityManagerFactory.createEntityManager().transaction { em ->
            cpiCpkRepository.findById(em, cpiCpkId)
        }!!

    private fun findCpkMetadata(fileChecksum: SecureHash) =
        entityManagerFactory.createEntityManager().transaction { em ->
            cpkRepository.findById(em, fileChecksum)
        }!!

    private fun findAndAssertCpks(
        combos: List<Pair<Cpi, Cpk>>,
        expectedCpkFileChecksum: SecureHash? = null,
        expectedMetadataEntityVersion: Int = 0,
        expectedFileEntityVersion: Int = 0,
        expectedCpiCpkEntityVersion: Int = 0
    ) {
        combos.forEach { (cpi, cpk) ->
            val (pairEntityVersionAndCpkMetadata, cpkFile, cpiCpk) = entityManagerFactory.createEntityManager().transaction { em ->
                val cpkKey = cpk.metadata.fileChecksum
                val cpiCpk =
                    cpiCpkRepository.findById(
                        em,
                        CpiCpkIdentifier(
                            cpi.metadata.cpiId.name,
                            cpi.metadata.cpiId.version,
                            cpi.metadata.cpiId.signerSummaryHash,
                            cpk.metadata.fileChecksum))!!
                val pairEntityVersionAndCpkMetadata = cpkRepository.findById(em, cpkKey)!!
                val cpkFile = cpkFileRepository.findById(em, cpkKey)
                Triple(pairEntityVersionAndCpkMetadata, cpkFile, cpiCpk)
            }

            assertThat(pairEntityVersionAndCpkMetadata.second.fileChecksum)
                .isEqualTo(expectedCpkFileChecksum ?: cpk.metadata.fileChecksum)
            assertThat(cpkFile.fileChecksum)
                .isEqualTo(expectedCpkFileChecksum ?: cpk.metadata.fileChecksum)
            assertThat(pairEntityVersionAndCpkMetadata.second.toJsonAvro())
                .isEqualTo(cpk.metadata.toJsonAvro())

            assertThat(pairEntityVersionAndCpkMetadata.first)
                .withFailMessage(
                    "CpkMetadataEntity.entityVersion expected $expectedMetadataEntityVersion " +
                            "but was ${pairEntityVersionAndCpkMetadata.first}.")
                .isEqualTo(expectedMetadataEntityVersion)
            assertThat(cpkFile.version)
                .withFailMessage(
                    "CpkFileEntity.entityVersion expected $expectedFileEntityVersion " +
                            "but was ${cpkFile.version}.")
                .isEqualTo(expectedFileEntityVersion)
            assertThat(cpiCpk.entityVersion)
                .withFailMessage(
                    "CpiCpkEntity.entityVersion expected $expectedCpiCpkEntityVersion " +
                            "but was ${cpiCpk.entityVersion}.")
                .isEqualTo(expectedCpiCpkEntityVersion)
        }
    }
}
