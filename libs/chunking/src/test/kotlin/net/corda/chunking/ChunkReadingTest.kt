package net.corda.chunking

import com.google.common.jimfs.Jimfs
import net.corda.chunking.impl.ChunkReaderImpl
import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.chunking.impl.ChunkWriterImpl.Companion.MB
import net.corda.data.chunking.Chunk
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ChunkReadingTest {
    lateinit var fs: FileSystem

    @BeforeEach
    private fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    private fun afterEach() {
        fs.close()
    }

    private fun randomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length).map { allowedChars.random() }.joinToString("")
    }

    private fun createEmptyFile(fileSize: Long): Path {
        val path = fs.getPath(randomString(12))
        Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).apply {
            position(fileSize)
            write(ByteBuffer.wrap(ByteArray(0)))
        }

        assertThat(fileSize).isEqualTo(Files.size(path))
        return path
    }

    private fun randomId(): SecureHash = SecureHash("SHA2_256", randomString(10).toByteArray())

    @Test
    fun `can read in order chunks`() {
        var actualId: SecureHash? = null
        var actualPath: Path? = null
        val reader = ChunkReaderFactory.create(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { id, path ->
                actualId = id
                actualPath = path
            }
        }

        val writer = ChunkWriterFactory.create().apply {
            onChunk {
                reader.read(it)
            }
        }

        val path = createEmptyFile((2 * MB).toLong())
        val id = randomId()
        writer.write(id, Files.newInputStream(path))

        assertThat(id).isEqualTo(actualId)
        assertThat(Files.size(path)).isEqualTo(Files.size(actualPath))
    }

    @Test
    fun `incomplete read fails to complete`() {
        val chunks = mutableListOf<Chunk>()

        var readCompleted = false
        val reader = ChunkReaderFactory.create(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { _, _ -> readCompleted = true }
        }

        val chunkCount = 5

        val writer = ChunkWriterImpl(32).apply {
            // guaranteed to be in order in this test
            onChunk { chunks.add(it) }
        }

        val path = createEmptyFile((chunkCount * writer.chunkSize).toLong())
        val id = randomId()
        writer.write(id, Files.newInputStream(path))
        assertThat(chunks.size).isGreaterThan(2)

        // Now replay first and last chunks, but not the rest.
        assertThat(chunks.last().payload.limit()).isEqualTo(0)

        reader.read(chunks.last())
        reader.read(chunks.first())

        assertThat(readCompleted).isFalse
    }

    @Test
    fun `can read out of order chunks`() {
        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterImpl(32).apply {
            onChunk { chunks.add(it) }
        }

        var actualId: SecureHash? = null
        var actualPath: Path? = null
        var readCompleted = false
        val reader = ChunkReaderFactory.create(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { id, path ->
                actualId = id
                actualPath = path
                readCompleted = true
            }
        }

        val chunkCount = 5
        val path = createEmptyFile((chunkCount * writer.chunkSize).toLong())
        val id = randomId()
        writer.write(id, Files.newInputStream(path))

        // Guaranteed to be in order in this test
        // Now replay two chunks
        assertThat(chunks.last().payload.limit()).isEqualTo(0)
        assertThat(chunks.first().chunkNumber).isEqualTo(0)

        // read first and last
        reader.read(chunks.last())
        chunks.removeLast()
        reader.read(chunks.first())
        chunks.removeFirst()

        // read remaining
        chunks.forEach { reader.read(it) }

        assertThat(readCompleted).isTrue
        assertThat(id).isEqualTo(actualId)
        assertThat(Files.size(path)).isEqualTo(Files.size(actualPath))
    }

    @Test
    fun `can read overlapping files with out of order chunks`() {
        val chunks = mutableListOf<Chunk>()
        val chunkSize = 32 //bytes
        val writer = ChunkWriterImpl(chunkSize).apply {
            onChunk { chunk -> chunks.add(chunk) }
        }

        var completionCount = 0

        val reader = ChunkReaderImpl(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { _, _ -> completionCount++ }
        }

        val fileCount = 5
        var expectedNonZeroChunkCount = 0
        for (i in 1..fileCount) {
            val chunkCount = (1..6).random()
            expectedNonZeroChunkCount += chunkCount
            val path = createEmptyFile((chunkCount * writer.chunkSize).toLong())
            val id = randomId()
            writer.write(id, Files.newInputStream(path))
        }

        // non-zero chunks plus  one zero chunk per file
        assertThat(chunks.size).isEqualTo(expectedNonZeroChunkCount + fileCount)

        // Now completely randomize the chunks
        chunks.shuffle()
        chunks.forEach { reader.read(it) }

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

        val expectedPath = fs.getPath(randomString(12))
        Files.newBufferedWriter(expectedPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
            it.write(loremIpsum)
        }

        val divisor = 10
        val chunkSize = loremIpsum.length / divisor
        assertThat(chunkSize * 10)
            .withFailMessage("The test string should not be a multiple of $divisor so that we have a final odd sized chunk ")
            .isNotEqualTo(loremIpsum.length)

        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterImpl(chunkSize).apply {
            onChunk { chunks.add(it) }
        }

        val id = randomId()
        writer.write(id, Files.newInputStream(expectedPath))

        lateinit var actualPath: Path
        lateinit var actualId: SecureHash
        val reader = ChunkReaderImpl(Files.createDirectory(fs.getPath("temp"))).apply {
            onComplete { secureHash, path ->
                actualPath = path
                actualId = secureHash
            }
        }

        // make them out of order
        chunks.shuffle()
        chunks.forEach { reader.read(it) }

        assertThat(id).isEqualTo(actualId)

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
        val id = randomId()
        val chunks = mutableListOf<Chunk>()
        val chunkSize = 32 //bytes
        val writer = ChunkWriterImpl(chunkSize).apply {
            onChunk { chunks.add(it) }
        }
        writer.write(id, Files.newInputStream(path))

        assertThat(chunks.size).isEqualTo(1)
        assertThat(chunks.last().payload.limit()).isEqualTo(0)
    }
}
