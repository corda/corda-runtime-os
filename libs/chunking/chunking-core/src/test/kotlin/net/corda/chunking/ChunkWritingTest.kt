package net.corda.chunking

import com.google.common.jimfs.Jimfs
import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.chunking.impl.ChunkWriterImpl.Companion.KB
import net.corda.chunking.impl.ChunkWriterImpl.Companion.MB
import net.corda.data.chunking.Chunk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

class ChunkWritingTest {
    lateinit var fs: FileSystem

    @BeforeEach
    private fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    private fun afterEach() {
        fs.close()
    }

    private fun createFile(fileSize: Long): Path {
        val path = randomFileName()
        Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).apply {
            position(fileSize)
            write(ByteBuffer.wrap(ByteArray(0)))
        }

        assertThat(fileSize).isEqualTo(Files.size(path))
        return path
    }

    private fun randomFileName(): Path = fs.getPath(UUID.randomUUID().toString())

    @Test
    fun `simple chunking`() {
        val chunkWriter = ChunkWriterFactory.create(1 * MB)
        var chunkWrittenCount = 0
        chunkWriter.onChunk { chunkWrittenCount++ }

        val path = createFile((2 * MB).toLong())

        Files.newInputStream(path).use { chunkWriter.write(randomFileName(), it) }

        assertThat(chunkWrittenCount).isGreaterThan(0)
    }

    @Test
    fun `last chunk is zero size`() {
        val chunkWriter = ChunkWriterFactory.create(1 * MB)
        var lastChunkSize = 0
        chunkWriter.onChunk { lastChunkSize = it.data.limit() }

        val path = createFile((2 * MB).toLong())

        Files.newInputStream(path).use { chunkWriter.write(randomFileName(), it) }

        assertThat(0).isEqualTo(lastChunkSize)
    }

    @Test
    fun `chunk file name is set correctly`() {
        val fileName = randomFileName()
        val writer = ChunkWriterFactory.create(1 * MB).apply {
            onChunk { assertThat(it.fileName).isEqualTo(fileName.toString()) }
        }

        val path = createFile((32 * KB).toLong())
        writer.write(fileName, Files.newInputStream(path))
    }

    @Test
    fun `multiple chunks are written`() {
        var count = 0
        val writer = ChunkWriterImpl(32 * KB).apply {
            onChunk { count++ }
        }

        val chunks = 5
        val path = createFile((chunks * writer.chunkSize).toLong())
        writer.write(randomFileName(), Files.newInputStream(path))

        val expected = chunks + 1 // same chunks, plus a zero sized one.
        assertThat(expected).isEqualTo(count)
    }

    @Test
    fun `ensure chunks are trimmed to minimum size`() {
        val chunkSize = 32 * KB
        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterImpl(chunkSize).apply {
            onChunk { chunks.add(it) }
        }

        val chunkCount = 5
        val excessBytes = 55
        val fileSize = chunkCount * chunkSize + excessBytes
        val path = createFile(fileSize.toLong())
        writer.write(randomFileName(), Files.newInputStream(path))

        assertThat(chunkCount + 2).isEqualTo(chunks.size)

        // remove the zero chunk.
        val zeroChunk = chunks.removeLast()
        assertThat(zeroChunk.data.limit()).isEqualTo(0)

        val lastNonZeroChunk = chunks.last()
        assertThat(lastNonZeroChunk.data.capacity()).isNotEqualTo(chunkSize)
        assertThat(lastNonZeroChunk.data.limit()).isNotEqualTo(chunkSize)

        assertThat(lastNonZeroChunk.data.limit()).isEqualTo(excessBytes)
        assertThat(lastNonZeroChunk.data.capacity()).isEqualTo(excessBytes)
    }
}
