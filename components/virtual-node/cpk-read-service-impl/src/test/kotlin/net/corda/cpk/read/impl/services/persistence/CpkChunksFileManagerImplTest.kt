package net.corda.cpk.read.impl.services.persistence

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.chunking.toAvro
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl.Companion.toCpkDirName
import net.corda.data.chunking.CpkChunkId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystem
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl.Companion.toFileName
import net.corda.data.chunking.Chunk
import net.corda.utilities.inputStream
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class CpkChunksFileManagerImplTest {
    private lateinit var commonCpkCacheDir: Path
    private lateinit var cpkChunksFileManagerImpl: CpkChunksFileManagerImpl

    private lateinit var fs: FileSystem

    companion object {
        const val DUMMY_HASH = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
    }

    @BeforeEach
    fun setUp() {
        fs = Jimfs.newFileSystem(Configuration.unix())
        commonCpkCacheDir = fs.getPath("/cpks")
        cpkChunksFileManagerImpl = CpkChunksFileManagerImpl(commonCpkCacheDir)
    }

    @AfterEach
    fun cleanUp() {
        fs.close()
    }

    private fun CpkChunkId.toPath(): Path {
        val cpkXDir = commonCpkCacheDir.resolve(fs.getPath(this.toCpkDirName()))
        if (!Files.exists(cpkXDir)) {
            Files.createDirectory(cpkXDir)
        }
        val fileName = this.toFileName()
        return fs.getPath(cpkXDir.resolve(fileName).toString())
    }

    private fun CpkChunkId.toDummyFile(): Path {
        val filePath = toPath()
        Files.newByteChannel(filePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).use {
            it.position(0)
            it.write(ByteBuffer.wrap(ByteArray(0)))
        }
        return filePath
    }

    private fun CpkChunkId.fileExists() =
        Files.exists(toPath())

    private fun getCpkChunkContent(chunkId: CpkChunkId): ByteArray {
        val filePath = chunkId.toPath()
        val inStream =  filePath.inputStream()
        return inStream.readAllBytes()
    }

    private fun dummyCpkChunkIdToChunk(
        cpkChecksum: SecureHash,
        cpkChunkPartNumber: Int,
        chunkingChecksum: SecureHash,
        bytes: ByteArray
    ) =
        CpkChunkId(cpkChecksum.toAvro(), cpkChunkPartNumber) to
                Chunk(
                    "dummyRequestId",
                    "dummyFileName",
                    chunkingChecksum.toAvro(),
                    cpkChunkPartNumber,
                    0,
                    ByteBuffer.wrap(bytes)
                )

    @Test
    fun `chunk file exists looks for requested cpk chunk id`() {
        val (cpkChunkId0, _) =
            dummyCpkChunkIdToChunk(SecureHash.create(DUMMY_HASH), 0, SecureHash.create(DUMMY_HASH), byteArrayOf())
        cpkChunkId0.toDummyFile()
        assertTrue(cpkChunksFileManagerImpl.chunkFileExists(cpkChunkId0))

        val (cpkChunkId1, _) =
            dummyCpkChunkIdToChunk(SecureHash.create(DUMMY_HASH), 1, SecureHash.create(DUMMY_HASH), byteArrayOf())
        assertFalse(cpkChunksFileManagerImpl.chunkFileExists(cpkChunkId1))
    }

    @Test
    fun `on write chunk file writes chunk file creates corresponding CPK directory`() {
        val (cpkChunkId, _) =
            dummyCpkChunkIdToChunk(SecureHash.create(DUMMY_HASH), 0, SecureHash.create(DUMMY_HASH), byteArrayOf())
        val filePath = cpkChunkId.toDummyFile()
        assertTrue(filePath.contains(fs.getPath(SecureHash.create(DUMMY_HASH).toHexString())))
    }

    @Test
    fun `on write chunk file writes chunk file`() {
        val bytes = byteArrayOf(0x01, 0x02)
        val (cpkChunkId, chunk) =
            dummyCpkChunkIdToChunk(SecureHash.create(DUMMY_HASH), 0, SecureHash.create(DUMMY_HASH), bytes)
        cpkChunksFileManagerImpl.writeChunkFile(cpkChunkId, chunk)
        assertTrue(cpkChunkId.fileExists())

        val bytesRead = getCpkChunkContent(cpkChunkId)
        assertTrue(bytes.contentEquals(bytesRead))
    }
}