package net.corda.chunking

import com.google.common.jimfs.Jimfs
import net.corda.chunking.Constants.Companion.APP_LEVEL_CHUNK_MESSAGE_OVERHEAD
import net.corda.chunking.Constants.Companion.CHUNK_FILENAME_KEY
import net.corda.chunking.Constants.Companion.KB
import net.corda.chunking.Constants.Companion.MB
import net.corda.chunking.impl.ChunkBuilderServiceImpl
import net.corda.chunking.impl.ChunkWriterImpl
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
    private lateinit var fs: FileSystem

    private val chunkBuilderService: ChunkBuilderService = ChunkBuilderServiceImpl()

    @BeforeEach
    fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    fun afterEach() {
        fs.close()
    }

    private fun createFile(fileSize: Long): Path {
        val path = fs.getPath(randomFileName())
        Files.newByteChannel(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use { channel ->
            channel.position(fileSize)
            channel.write(ByteBuffer.wrap(ByteArray(0)))
        }

        assertThat(fileSize).isEqualTo(Files.size(path))
        return path
    }

    private fun randomFileName(): String = UUID.randomUUID().toString()

    @Test
    fun `simple chunking`() {
        val chunkWriter = ChunkWriterFactory.create(1 * MB, emptyMap())
        var chunkWrittenCount = 0
        chunkWriter.onChunk { chunkWrittenCount++ }

        val path = createFile((2 * MB).toLong())

        Files.newInputStream(path).use { chunkWriter.write(it) }

        assertThat(chunkWrittenCount).isGreaterThan(0)
    }

    @Test
    fun `last chunk is zero size`() {
        val chunkWriter = ChunkWriterFactory.create(1 * MB)
        var lastChunkSize = 0
        chunkWriter.onChunk { lastChunkSize = it.data.limit() }

        val path = createFile((2 * MB).toLong())

        Files.newInputStream(path).use { chunkWriter.write(it) }

        assertThat(0).isEqualTo(lastChunkSize)
    }

    @Test
    fun `chunk file name is set correctly`() {
        val fileName = randomFileName()
        val properties = mapOf<String, String?>(CHUNK_FILENAME_KEY to fileName)
        val writer = ChunkWriterFactory.create(1 * MB, properties)
        writer.apply {
            this.onChunk { chunk ->
                assertThat(
                    chunk.properties.items.find
                        { it.key == CHUNK_FILENAME_KEY }?.value
                ).isEqualTo(fileName)
            }
        }

        val path = createFile((32 * KB).toLong())
        writer.write(Files.newInputStream(path))
    }

    @Test
    fun `multiple chunks are written`() {
        var count = 0
        val writer = ChunkWriterImpl(32 * KB, chunkBuilderService).apply {
            onChunk { count++ }
        }

        val chunks = 5
        val path = createFile((chunks * writer.chunkSize).toLong())
        writer.write(Files.newInputStream(path))

        val expected = chunks + 1 // same chunks, plus a zero sized one.
        assertThat(expected).isEqualTo(count)
    }

    @Test
    fun `ensure chunks are trimmed to minimum size`() {
        val chunkSize = 32 * KB
        val chunks = mutableListOf<Chunk>()
        val writer = ChunkWriterImpl(chunkSize + APP_LEVEL_CHUNK_MESSAGE_OVERHEAD, chunkBuilderService).apply {
            onChunk { chunks.add(it) }
        }

        val chunkCount = 5
        val excessBytes = 55
        val fileSize = chunkCount * chunkSize + excessBytes
        val path = createFile(fileSize.toLong())
        writer.write(Files.newInputStream(path))

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
