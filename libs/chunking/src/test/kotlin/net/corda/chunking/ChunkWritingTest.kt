package net.corda.chunking

import com.google.common.jimfs.Jimfs
import net.corda.chunking.impl.ChunkWriterImpl
import net.corda.chunking.impl.ChunkWriterImpl.Companion.KB
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

    private fun randomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length).map { allowedChars.random() }.joinToString("")
    }

    private fun createFile(fileSize: Long): Path {
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
    fun `simple chunking`() {
        val chunkWriter = ChunkWriterFactory.create()
        var chunkWrittenCount = 0
        chunkWriter.onChunk { chunkWrittenCount++ }

        val path = createFile((2 * MB).toLong())

        chunkWriter.write(randomId(), Files.newInputStream(path))

        assertThat(chunkWrittenCount).isGreaterThan(0)
    }

    @Test
    fun `last chunk is zero size`() {
        val chunkWriter = ChunkWriterFactory.create()
        var lastChunkSize = 0
        chunkWriter.onChunk { lastChunkSize = it.payload.limit() }

        val path = createFile((2 * MB).toLong())

        chunkWriter.write(randomId(), Files.newInputStream(path))

        assertThat(0).isEqualTo(lastChunkSize)
    }

    @Test
    fun `chunk id is set correctly`() {
        val id = randomId()
        val writer = ChunkWriterFactory.create().apply {
            onChunk { assertThat(it.identifier!!.toCorda()).isEqualTo(id) }
        }

        val path = createFile((32 * KB).toLong())
        writer.write(id, Files.newInputStream(path))
    }

    @Test
    fun `multiple chunks are written`() {
        var count = 0
        val writer = ChunkWriterImpl(32 * KB).apply {
            onChunk { count++ }
        }

        val chunks = 5
        val path = createFile((chunks * writer.chunkSize).toLong())
        writer.write(randomId(), Files.newInputStream(path))

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
        writer.write(randomId(), Files.newInputStream(path))

        assertThat(chunkCount + 2).isEqualTo(chunks.size)

        // remove the zero chunk.
        val zeroChunk = chunks.removeLast()
        assertThat(zeroChunk.payload.limit()).isEqualTo(0)

        val lastNonZeroChunk = chunks.last()
        assertThat(lastNonZeroChunk.payload.capacity()).isNotEqualTo(chunkSize)
        assertThat(lastNonZeroChunk.payload.limit()).isNotEqualTo(chunkSize)

        assertThat(lastNonZeroChunk.payload.limit()).isEqualTo(excessBytes)
        assertThat(lastNonZeroChunk.payload.capacity()).isEqualTo(excessBytes)
    }
}
