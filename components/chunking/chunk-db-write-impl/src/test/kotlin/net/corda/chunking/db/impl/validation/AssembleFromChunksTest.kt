package net.corda.chunking.db.impl.validation

import com.google.common.jimfs.Jimfs
import net.corda.chunking.ChunkReader
import net.corda.chunking.ChunkReaderFactory
import net.corda.chunking.ChunksCombined
import net.corda.chunking.RequestId
import net.corda.chunking.db.impl.AllChunksReceived
import net.corda.chunking.db.impl.persistence.ChunkPersistence
import net.corda.crypto.core.parseSecureHash
import net.corda.data.chunking.Chunk
import net.corda.libs.cpiupload.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.FileSystem
import java.nio.file.Files
import java.util.UUID

internal class AssembleFromChunksTest {
    lateinit var fs: FileSystem

    @BeforeEach
    fun beforeEach() {
        fs = Jimfs.newFileSystem()
    }

    @AfterEach
    fun afterEach() {
        fs.close()
    }

    @Test
    fun `assemble succeeds with simple test`() {
        val cacheDir = fs.getPath("dest").also { Files.createDirectory(it) }
        val expectedTempDir = fs.getPath("expected")
        val chunkPersistence = object : ChunkPersistence {
            override fun persistChunk(chunk: Chunk): AllChunksReceived {
                TODO("Not yet implemented")
            }

            override fun checksumIsValid(requestId: RequestId): Boolean = true
            override fun forEachChunk(requestId: RequestId, onChunk: (chunk: Chunk) -> Unit) = onChunk(Chunk())
        }
        val requestId = UUID.randomUUID().toString()
        val expectedFileName = "some.cpi"
        val expectedChecksum = parseSecureHash("DUMMY:1234567890")
        val chunkReader = object : ChunkReader {
            var cb: ChunksCombined? = null
            override fun read(chunk: Chunk) {
                cb!!.onChunksCombined(expectedFileName, expectedTempDir, expectedChecksum, emptyMap())
            }

            override fun onComplete(chunksCombinedCallback: ChunksCombined) {
                cb = chunksCombinedCallback
            }
        }

        val chunkReaderFactory = mock<ChunkReaderFactory> { on { create(any()) }.doReturn(chunkReader) }

        val fileInfo = assembleFileFromChunks(cacheDir, chunkPersistence, requestId, chunkReaderFactory)

        assertThat(fileInfo.name).isEqualTo(expectedFileName)
        assertThat(fileInfo.path).isEqualTo(expectedTempDir)
        assertThat(fileInfo.checksum).isEqualTo(expectedChecksum)
        assertThat(fileInfo.properties!!.isEmpty()).isTrue
    }

    @Test
    fun `assemble fails if not complete`() {
        val cacheDir = fs.getPath("dest").also { Files.createDirectory(it) }
        val chunkPersistence = object : ChunkPersistence {
            override fun persistChunk(chunk: Chunk): AllChunksReceived {
                TODO("Not yet implemented")
            }

            override fun checksumIsValid(requestId: RequestId): Boolean = true
            override fun forEachChunk(requestId: RequestId, onChunk: (chunk: Chunk) -> Unit) = onChunk(Chunk())
        }
        val requestId = UUID.randomUUID().toString()

        // If complete-callback is not called, we never assemble the file from the chunks
        val noCallbackChunkReader = object : ChunkReader {
            override fun read(chunk: Chunk) {}
            override fun onComplete(chunksCombinedCallback: ChunksCombined) {}
        }

        val chunkReaderFactory = mock<ChunkReaderFactory> { on { create(any()) }.doReturn(noCallbackChunkReader) }

        assertThrows<ValidationException> {
            assembleFileFromChunks(cacheDir, chunkPersistence, requestId, chunkReaderFactory)
        }
    }
}
