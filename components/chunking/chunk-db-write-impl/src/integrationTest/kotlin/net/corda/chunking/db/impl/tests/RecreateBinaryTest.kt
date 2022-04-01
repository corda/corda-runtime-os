package net.corda.chunking.db.impl.tests

import com.google.common.jimfs.Jimfs
import net.corda.chunking.ChunkReaderFactory
import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.datamodel.ChunkingEntities
import net.corda.chunking.db.impl.persistence.DatabaseChunkPersistence
import net.corda.data.chunking.Chunk
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecreateBinaryTest {
    private val emConfig = DbUtils.getEntityManagerConfiguration("chunking_db_for_test")
    private val entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
        "test_unit",
        ChunkingEntities.classes.toList(),
        emConfig
    )
    private val persistence = DatabaseChunkPersistence(entityManagerFactory)
    private val loremIpsum = """
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

    private fun randomFileName(): String = UUID.randomUUID().toString()

    companion object {
        // N.B.  We're pulling in the config tables as well.
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
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
        entityManagerFactory.close()
    }

    private fun createChunks(someFile: String, tempFile: Path): MutableList<Chunk> {
        val divisor = 10
        val chunkSize = loremIpsum.length / divisor

        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterFactory.create(chunkSize).apply {
            onChunk { chunks.add(it) }
        }
        // end of setup...

        // This is what we'd write in one of our components
        writer.write(someFile, Files.newInputStream(tempFile))
        return chunks
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

    private fun createFile(): Path {
        val tempFile = randomPathName()

        Files.newBufferedWriter(tempFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
            it.write(loremIpsum)
        }
        return tempFile
    }

    /**
     * Remove this test if we stop using ChunkReader
     */
    @Test
    fun `can chunk and combine binary as file`() {
        val expectedFileName = "abc-1.0.0.cpb"
        val originalFile = createFile()
        val chunks = createChunks(expectedFileName, originalFile)

        var actualFileName = ""
        var tempPath: Path? = null
        val destDir = fs.getPath("destDir").apply { Files.createDirectories(this) }

        val chunkReader = ChunkReaderFactory.create(destDir).apply {
            this.onComplete { originalFileName: String, tempPathOfBinary: Path, _: SecureHash ->
                actualFileName = originalFileName
                tempPath = tempPathOfBinary
            }
        }

        // Put chunks
        val requestId = chunks.first().requestId
        chunks.forEach { persistence.persistChunk(it) }

        // Get chunks back, and write straight to file.
        persistence.forEachChunk(requestId) { chunkReader.read(it) }
        assertThat(actualFileName).isEqualTo(expectedFileName)

        val expectedFileContent = Files.readString(originalFile)
        val actualFileContent = Files.readString(tempPath)
        assertThat(actualFileContent).isEqualTo(expectedFileContent)
    }

    @Test
    fun `can chunk and get same chunks back`() {
        val expectedChunks = createChunks("abc-1.0.0.cpb", createFile())

        // Put chunks
        val requestId = expectedChunks.first().requestId
        expectedChunks.forEach { persistence.persistChunk(it) }

        // Get chunks
        val actualChunks = mutableListOf<Chunk>()
        persistence.forEachChunk(requestId) { actualChunks.add(it) }

        assertThat(actualChunks.isNotEmpty()).isTrue
        assertThat(actualChunks.size).isEqualTo(expectedChunks.size)
        (actualChunks zip expectedChunks).forEach { assertThat(it.first).isEqualTo(it.second) }
    }

    @Test
    fun `missing chunk not found`() {
        val expectedChunks = createChunks("abc-1.0.0.cpb", createFile())

        // Put chunks
        val requestId = expectedChunks.first().requestId
        val droppedChunks = 1
        val missingFirstChunk = expectedChunks.drop(droppedChunks)
        missingFirstChunk.forEach { persistence.persistChunk(it) }

        // Get chunks
        val actualChunks = mutableListOf<Chunk>()
        persistence.forEachChunk(requestId) { actualChunks.add(it) }

        assertThat(actualChunks.isNotEmpty()).isTrue
        assertThat(actualChunks.size).isNotEqualTo(expectedChunks.size)
        assertThat(expectedChunks.size - actualChunks.size).isEqualTo(droppedChunks)
    }
}
