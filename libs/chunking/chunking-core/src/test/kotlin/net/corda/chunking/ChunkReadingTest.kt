package net.corda.chunking

import com.google.common.jimfs.Jimfs
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import net.corda.chunking.Constants.Companion.CORDA_MESSAGE_OVERHEAD
import net.corda.chunking.Constants.Companion.MB
import net.corda.chunking.impl.ChunkBuilderServiceImpl
import net.corda.chunking.impl.ChunkReaderImpl
import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.data.chunking.Chunk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChunkReadingTest {
    private lateinit var fs: FileSystem

    private val chunkReaderFactory = ChunkReaderFactoryImpl
    private val chunkBuilderService = ChunkBuilderServiceImpl()

    @BeforeEach
    fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    fun afterEach() {
        fs.close()
    }

    private fun createEmptyFile(fileSize: Long): Path {
        val path = fs.getPath(randomFileName())
        Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).apply {
            position(fileSize)
            write(ByteBuffer.wrap(ByteArray(0)))
        }

        assertThat(fileSize).isEqualTo(Files.size(path))
        return path
    }

    private fun randomFileName(): String = UUID.randomUUID().toString()

    @Test
    fun `can read in order chunks`() {
        var actualFileName: String? = null
        var actualPath: Path? = null
        val reader = chunkReaderFactory.create(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { originalFileName, tempBinaryPath, _, _ ->
                actualFileName = originalFileName
                actualPath = tempBinaryPath
            }
        }

        val writer = ChunkWriterFactory.create(1 * MB).apply {
            onChunk(reader::read)
        }

        val path = createEmptyFile((2 * MB).toLong())
        val ourFileName = randomFileName()
        writer.write(ourFileName, Files.newInputStream(path))

        assertThat(ourFileName).isEqualTo(actualFileName.toString())
        assertThat(Files.size(path)).isEqualTo(Files.size(actualPath!!))
    }

    @Test
    fun `incomplete read fails to complete`() {
        val chunks = mutableListOf<Chunk>()

        var readCompleted = false
        val reader = chunkReaderFactory.create(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { _, _, _, _ -> readCompleted = true }
        }

        val chunkCount = 5

        val writer = ChunkWriterImpl(32 + CORDA_MESSAGE_OVERHEAD, chunkBuilderService).apply {
            // guaranteed to be in order in this test
            onChunk(chunks::add)
        }

        val path = createEmptyFile((chunkCount * writer.chunkSize).toLong())
        val ourFileName = randomFileName()
        writer.write(ourFileName, Files.newInputStream(path))
        assertThat(chunks.size).isGreaterThan(2)

        // Now replay first and last chunks, but not the rest.
        assertThat(chunks.last().data.limit()).isEqualTo(0)

        reader.read(chunks.last())
        reader.read(chunks.first())

        assertThat(readCompleted).isFalse
    }

    @Test
    fun `can read out of order chunks`() {
        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterImpl(32 + CORDA_MESSAGE_OVERHEAD, chunkBuilderService).apply {
            onChunk(chunks::add)
        }

        var actualFileName: String? = null
        var actualPath: Path? = null
        var readCompleted = false
        val reader = chunkReaderFactory.create(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { originalFileName, tempBinaryPath, _, _ ->
                actualFileName = originalFileName
                actualPath = tempBinaryPath
                readCompleted = true
            }
        }

        val chunkCount = 5
        val path = createEmptyFile((chunkCount * writer.chunkSize).toLong())
        val ourFileName = randomFileName()
        writer.write(ourFileName, Files.newInputStream(path))

        // Guaranteed to be in order in this test
        // Now replay two chunks
        assertThat(chunks.last().data.limit()).isEqualTo(0)
        assertThat(chunks.first().partNumber).isEqualTo(0)

        // read first and last
        reader.read(chunks.last())
        chunks.removeLast()
        reader.read(chunks.first())
        chunks.removeFirst()

        // read remaining
        chunks.forEach(reader::read)

        assertThat(readCompleted).isTrue
        // "cast away" the underlying filesystem (otherwise we get jimfs vs unix paths)
        assertThat(ourFileName).isEqualTo(actualFileName.toString())
        assertThat(Files.size(path)).isEqualTo(Files.size(actualPath!!))
    }

    @Test
    fun `can read overlapping files with out of order chunks`() {
        val chunks = mutableListOf<Chunk>()
        val chunkSize = 32 + CORDA_MESSAGE_OVERHEAD //bytes
        val writer = ChunkWriterImpl(chunkSize, chunkBuilderService).apply {
            onChunk(chunks::add)
        }

        var completionCount = 0

        val reader = ChunkReaderImpl(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { _, _, _, _ -> completionCount++ }
        }

        val fileCount = 5
        var expectedNonZeroChunkCount = 0
        for (i in 1..fileCount) {
            val chunkCount = (1..6).random()
            expectedNonZeroChunkCount += chunkCount
            val path = createEmptyFile((chunkCount * writer.chunkSize).toLong())
            val ourFileName = randomFileName()
            writer.write(ourFileName, Files.newInputStream(path))
        }

        // non-zero chunks plus  one zero chunk per file
        assertThat(chunks.size).isEqualTo(expectedNonZeroChunkCount + fileCount)

        // Now completely randomize the chunks
        chunks.shuffle()
        chunks.forEach(reader::read)

        assertThat(completionCount).isEqualTo(fileCount)
    }

    @Test
    fun `can chunk a file with content and check result`() {
        val loremIpsum = """
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

        val expectedPath = fs.getPath(randomFileName())
        Files.newBufferedWriter(expectedPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
            it.write(loremIpsum)
        }

        val divisor = 10
        val chunkSize = (loremIpsum.length / divisor) + CORDA_MESSAGE_OVERHEAD
        assertThat(chunkSize * 10)
            .withFailMessage("The test string should not be a multiple of $divisor so that we have a final odd sized chunk ")
            .isNotEqualTo(loremIpsum.length)

        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterImpl(chunkSize, chunkBuilderService).apply {
            onChunk(chunks::add)
        }

        val ourFileName = randomFileName()
        writer.write(ourFileName, Files.newInputStream(expectedPath))

        lateinit var actualPath: Path
        lateinit var actualFileName: String
        val reader = ChunkReaderImpl(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { originalFileName, tempPathOfBinary, _, _ ->
                actualPath = tempPathOfBinary
                actualFileName = originalFileName
            }
        }

        // make them out of order
        chunks.shuffle()
        chunks.forEach(reader::read)

        // "cast away" the underlying filesystem (otherwise we get jimfs vs unix paths)
        assertThat(ourFileName).isEqualTo(actualFileName)

        val expectedFileContent = Files.newBufferedReader(expectedPath).use {
            it.readLines().joinToString("\n")
        }

        val actualFileContent = Files.newBufferedReader(actualPath).use {
            it.readLines().joinToString("\n")
        }

        assertThat(expectedFileContent).isEqualTo(actualFileContent)
        assertThat(expectedFileContent).isEqualTo(loremIpsum)
        assertThat(actualFileContent).isEqualTo(loremIpsum)
    }

    @Test
    fun `zero sized file`() {
        // Should never get a zero sized file and just the terminating chunk, but who knows?
        val path = createEmptyFile(0)
        val ourFileName = randomFileName()
        val chunks = mutableListOf<Chunk>()
        val chunkSize = 32 + CORDA_MESSAGE_OVERHEAD //bytes
        val writer = ChunkWriterImpl(chunkSize, chunkBuilderService).apply {
            onChunk(chunks::add)
        }
        writer.write(ourFileName, Files.newInputStream(path))

        assertThat(chunks.size).isEqualTo(1)
        assertThat(chunks.last().data.limit()).isEqualTo(0)
    }
}
