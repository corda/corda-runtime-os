package net.corda.chunking.db.impl.tests

import com.google.common.jimfs.Jimfs
import net.corda.chunking.ChunkWriter
import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.RequestId
import net.corda.chunking.datamodel.ChunkEntity
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.db.impl.ChunkDbWriterImpl
import net.corda.chunking.db.impl.persistence.DatabaseChunkPersistence
import net.corda.chunking.toAvro
import net.corda.data.chunking.Chunk
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.libs.cpi.datamodel.CpiEntities
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpiMetadataEntityKey
import net.corda.libs.cpi.datamodel.CpkFileEntity
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.ManifestCorDappInfo
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
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Random
import java.util.UUID
import javax.persistence.EntityManagerFactory
import javax.persistence.NoResultException
import javax.persistence.NonUniqueResultException
import javax.persistence.PersistenceException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DatabaseChunkPersistenceTest {
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
    private val persistence = DatabaseChunkPersistence(entityManagerFactory)
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

    private val random = Random(0)

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
        entityManagerFactory.close()
    }

    /** Return the parts we've received - i.e. chunks with non-zero bytes */
    fun partsReceived(entityManagerFactory: EntityManagerFactory, requestId: RequestId): Long {
        return entityManagerFactory.createEntityManager().transaction {
            it.createQuery(
                "SELECT count(c) FROM ${ChunkEntity::class.simpleName} c " +
                        "WHERE c.requestId = :requestId AND c.data IS NOT NULL"
            )
                .setParameter("requestId", requestId)
                .singleResult as Long
        }
    }

    /** Return the expected number of parts - i.e. the part number on the zero bytes chunk */
    fun partsExpected(entityManagerFactory: EntityManagerFactory, requestId: RequestId): Long {
        return entityManagerFactory.createEntityManager().transaction {
            try {
                (it.createQuery(
                    "SELECT c.partNumber FROM ${ChunkEntity::class.simpleName} c " +
                            "WHERE c.requestId = :requestId and c.data IS NULL"
                )
                    .setParameter("requestId", requestId)
                    .singleResult as Int).toLong()
            } catch (ex: NoResultException) {
                0L
            } catch (ex: NonUniqueResultException) {
                throw ex
            }
        }
    }

    lateinit var fs: FileSystem

    @BeforeEach
    private fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    private fun afterEach() {
        fs.close()
    }

    private fun randomPathName(): Path = fs.getPath(UUID.randomUUID().toString())

    private fun randomFileName(): String = UUID.randomUUID().toString()

    private fun createChunks(someFile: String): MutableList<Chunk> {
        val tempFile = randomPathName()
        Files.newBufferedWriter(tempFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
            it.write(mockCpkContent)
        }

        val divisor = 10
        val chunkSize = mockCpkContent.length / divisor
        assertThat(chunkSize * 10)
            .withFailMessage("The test string should not be a multiple of $divisor so that we have a final odd sized chunk ")
            .isNotEqualTo(mockCpkContent.length)
        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterFactory.create(chunkSize + 10240).apply {
            onChunk { chunks.add(it) }
        }
        // end of setup...

        // This is what we'd write in one of our components
        writer.write(someFile, Files.newInputStream(tempFile))
        return chunks
    }

    private fun mockCpk(hash: SecureHash, name: String) = mock<Cpk>().also { cpk ->
        val cpkId = CpkIdentifier(
            name = "cpk-name",
            version = "cpk-version",
            signerSummaryHash = hash,
        )

        val cpkManifest = CpkManifest(CpkFormatVersion(1,0))

        val cordappManifest = CordappManifest(
            "", "", -1, -1,
            ManifestCorDappInfo(null, null, null, null),
            ManifestCorDappInfo(null, null, null, null),
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
            fileChecksum = hash,
            cordappCertificates = emptySet(),
            timestamp = Instant.now(),
        )
        whenever(cpk.path).thenReturn(mockCpkContent.writeToPath())
        whenever(cpk.originalFileName).thenReturn(name)
        whenever(cpk.metadata).thenReturn(metadata)
    }

    private fun mockCpi(cpks: Collection<Cpk>): Cpi {
        // We need a random name here as the database primary key is (name, version, signerSummaryHash)
        // and we'd end up trying to insert the same mock cpi.
        val id = mock<CpiIdentifier> {
            whenever(it.name).thenReturn("test " + UUID.randomUUID().toString())
            whenever(it.version).thenReturn("1.0")
            whenever(it.signerSummaryHash).thenReturn(SecureHash("SHA-256", ByteArray(12)))
        }

        return mockCpiWithId(cpks, id)
    }



    private fun mockCpiWithId(cpks: Collection<Cpk>, cpiId: CpiIdentifier): Cpi {
        val metadata = mock<CpiMetadata>().also {
            whenever(it.cpiId).thenReturn(cpiId)
            whenever(it.groupPolicy).thenReturn("{}")
        }

        val cpi = mock<Cpi>().also {
            whenever(it.cpks).thenReturn(cpks)
            whenever(it.metadata).thenReturn(metadata)
        }

        return cpi
    }

    @Test
    fun `database chunk persistence writes chunks`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        chunks.shuffle()

        chunks.forEach { persistence.persistChunk(it) }

        val actual = entityManagerFactory.createEntityManager().transaction {
            it.createQuery("SELECT count(c) FROM ${ChunkEntity::class.simpleName} c WHERE c.requestId = :requestId")
                .setParameter("requestId", requestId)
                .singleResult as Long
        }

        assertThat(actual).isEqualTo(chunks.size.toLong())
    }

    @Test
    fun `database chunk persistence writes chunks for incomplete upload`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse

        chunks.removeLast()

        var status = AllChunksReceived.NO
        chunks.forEach { status = persistence.persistChunk(it) }
        assertThat(status).isEqualTo(AllChunksReceived.NO)
    }

    @Test
    fun `database chunk persistence writes chunks to completion`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse

        val last = chunks.last()
        chunks.removeLast()

        var status = AllChunksReceived.NO
        chunks.forEach { status = persistence.persistChunk(it) }
        assertThat(status).isEqualTo(AllChunksReceived.NO)

        status = persistence.persistChunk(last)
        assertThat(status).isEqualTo(AllChunksReceived.YES)
    }

    @Test
    fun `database chunk persistence writes shuffled chunks to completion`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse

        chunks.shuffle()

        val last = chunks.last()
        chunks.removeLast()

        var status = AllChunksReceived.NO
        chunks.forEach { status = persistence.persistChunk(it) }
        assertThat(status).isEqualTo(AllChunksReceived.NO)

        status = persistence.persistChunk(last)
        assertThat(status).isEqualTo(AllChunksReceived.YES)
    }

    @Test
    fun `database chunk persistence reads chunks`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        chunks.shuffle()

        chunks.forEach { persistence.persistChunk(it) }

        val actual = entityManagerFactory.createEntityManager().transaction {
            it.createQuery("SELECT count(c) FROM ${ChunkEntity::class.simpleName} c WHERE c.requestId = :requestId")
                .setParameter("requestId", requestId)
                .singleResult as Long
        }
        assertThat(actual).isEqualTo(chunks.size.toLong())

        val partsReceived = partsReceived(entityManagerFactory, requestId)
        assertThat(partsReceived).isEqualTo(chunks.size.toLong() - 1)

        val partsExpected = partsExpected(entityManagerFactory, requestId)
        assertThat(partsExpected).isEqualTo(partsReceived)
    }

    @Test
    fun `database chunk persistence checks chunks written to db are read with correct sizes`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        // don't shuffle for this.

        var allChunksReceived = AllChunksReceived.NO
        chunks.forEach { allChunksReceived = persistence.persistChunk(it) }

        assertThat(allChunksReceived).isEqualTo(AllChunksReceived.YES)

        // In the "real world" we'd probably stream the results because the bytes are multi-MB in size.
        val entities = entityManagerFactory.createEntityManager().transaction {
            it.createQuery(
                "SELECT c FROM ${ChunkEntity::class.simpleName} c " +
                        "WHERE c.requestId = :requestId " +
                        "ORDER BY c.partNumber ASC",
                ChunkEntity::class.java
            )
                .setParameter("requestId", requestId)
                .resultList
        }

        assertThat(chunks.size).isEqualTo(entities.size)
        assertThat(chunks.first().requestId).isEqualTo(entities.first().requestId)

        chunks.zip(entities).forEach {
            assertThat(it.first.partNumber).isEqualTo(it.second.partNumber)
            assertThat(it.first.data.capacity()).isEqualTo(it.second.data?.size ?: 0)
            assertThat(it.first.data.limit()).isEqualTo(it.second.data?.size ?: 0)
        }
    }

    @Test
    fun `database chunk persistence chunk checksums match`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId
        // don't shuffle for this.

        chunks.forEach { persistence.persistChunk(it) }

        assertThat(persistence.checksumIsValid(requestId)).isTrue
    }

    @Test
    fun `database chunk persistence returns false when broken checksums do not match`() {
        val someFile = randomFileName()
        val chunks = createChunks(someFile)
        assertThat(chunks.isEmpty()).isFalse
        val requestId = chunks.first().requestId

        chunks.last().checksum = SecureHash("rubbish", "1234567890".toByteArray()).toAvro()
        chunks.forEach { persistence.persistChunk(it) }

        assertThat(persistence.checksumIsValid(requestId)).isFalse
    }

    fun String.writeToPath(): Path {
        val path = fs.getPath(UUID.randomUUID().toString())
        Files.writeString(path, this)
        return path
    }

    /**
     * Various db tools show a persisted cpk (or bytes) as just a textual 'handle' to the blob of bytes,
     * so explicitly test here that it's actually doing what we think it is (persisting the bytes!).
     */
    @Test
    fun `database chunk persistence writes data and can be read back`() {
        val checksum = SecureHash.create("DUMMY:1234567890abcdef")
        val cpks = listOf(mockCpk(checksum, "1.cpk"))
        val cpi = mockCpi(cpks)

        persistence.persistMetadataAndCpks(cpi, "test.cpi", checksum, UUID.randomUUID().toString(), "abcdef")

        val cpkDataEntity = entityManagerFactory.createEntityManager().transaction {
            it.find(CpkFileEntity::class.java, checksum.toString())
        }!!

        assertThat(cpkDataEntity.data).isEqualTo(mockCpkContent.toByteArray())
    }

    @Test
    fun `database chunk persistence can lookup persisted cpi by checksum`() {
        val checksum = SecureHash.create("DUMMY:deadbeefdeadabcdef1234")
        assertThat(persistence.cpkExists(checksum)).isFalse

        val cpks = listOf(mockCpk(checksum, "1.cpk"))
        val cpi = mockCpi(cpks)
        persistence.persistMetadataAndCpks(cpi, "someFileName.cpi", checksum, UUID.randomUUID().toString(), "abcdef")
        assertThat(persistence.cpkExists(checksum)).isTrue
    }

    @Test
    fun `database chunk persistence can write multiple cpks into database`() {
        val cpks = listOf(
            mockCpk(SecureHash.create("AAA:1234567890abcd"), "1.cpk"),
            mockCpk(SecureHash.create("BBB:2345678901abcd"), "2.cpk"),
            mockCpk(SecureHash.create("CCC:3456789012abcd"), "3.cpk"),
        )
        val checksum = SecureHash.create("DUMMY:deadbeefdeadbeef")

        val cpi = mockCpi(cpks)

        persistence.persistMetadataAndCpks(cpi, "test.cpi", checksum, UUID.randomUUID().toString(), "123456")

        assertThrows<PersistenceException> {
            persistence.persistMetadataAndCpks(cpi, "test.cpi", checksum, UUID.randomUUID().toString(), "123456")
        }
    }

    @Test
    fun `database chunk persistence can write multiple CPIs with shared CPKs into database`() {
        val sharedCpk = mockCpk(
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
            "1.cpk")
        val cpi1 = mockCpi(listOf(
            sharedCpk,
            mockCpk(SecureHash.create("BBB:2345678901abcd"), "2.cpk"),
        ))

        persistence.persistMetadataAndCpks(
            cpi1,
            "test.cpi",
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
            UUID.randomUUID().toString(),
            "123456")

        val cpi2 = mockCpi(listOf(
            sharedCpk,
            mockCpk(
                SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
                "3.cpk"),
        ))

        assertDoesNotThrow {
            persistence.persistMetadataAndCpks(
                cpi2,
                "test.cpi",
                SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
                UUID.randomUUID().toString(),
                "123456")
        }
    }

    @Test
    fun `database chunk persistence can force update a CPI`() {
        val cpiChecksum = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
        val cpkChecksum = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
        val cpk1 = mockCpk(cpkChecksum, "1.cpk")
        val cpks = listOf(cpk1)
        val cpi = mockCpi(cpks)

        persistence.persistMetadataAndCpks(cpi, "test.cpi", cpiChecksum, UUID.randomUUID().toString(), "abcdef")

        val updatedCpiChecksum = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
        val updatedCpkChecksum = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
        val updatedCpks = listOf(cpk1, mockCpk(updatedCpkChecksum, "2.cpk"))
        // cpi with different CPKs but same ID
        val updatedCpi = mockCpiWithId(updatedCpks, cpi.metadata.cpiId)

        persistence.updateMetadataAndCpks(updatedCpi, "test.cpi", updatedCpiChecksum, UUID.randomUUID().toString(), "abcdef")

        val loadedCpi = entityManagerFactory.createEntityManager().transaction {
            it.find(CpiMetadataEntity::class.java, CpiMetadataEntityKey(
                updatedCpi.metadata.cpiId.name,
                updatedCpi.metadata.cpiId.version,
                updatedCpi.metadata.cpiId.signerSummaryHash.toString(),
            ))
        }!!

        assertThat(loadedCpi.cpks.size).isEqualTo(2)
    }

    @Test
    fun `database chunk persistence can force update the same CPI`() {
        val cpiChecksum = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
        val cpkChecksum = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
        val cpk1 = mockCpk(cpkChecksum, "1.cpk")
        val cpks = listOf(cpk1)
        val cpi = mockCpi(cpks)

        persistence.persistMetadataAndCpks(cpi, "test.cpi", cpiChecksum, UUID.randomUUID().toString(), "abcdef")

        val loadedCpi = entityManagerFactory.createEntityManager().transaction {
            it.find(CpiMetadataEntity::class.java, CpiMetadataEntityKey(
                cpi.metadata.cpiId.name,
                cpi.metadata.cpiId.version,
                cpi.metadata.cpiId.signerSummaryHash.toString(),
            ))
        }!!

        // force update same CPI
        persistence.updateMetadataAndCpks(cpi, "test.cpi", cpiChecksum, UUID.randomUUID().toString(), "abcdef")

        val updatedCpi = entityManagerFactory.createEntityManager().transaction {
            it.find(CpiMetadataEntity::class.java, CpiMetadataEntityKey(
                cpi.metadata.cpiId.name,
                cpi.metadata.cpiId.version,
                cpi.metadata.cpiId.signerSummaryHash.toString(),
            ))
        }!!

        assertThat(updatedCpi.insertTimestamp).isAfter(loadedCpi.insertTimestamp)
    }
}
