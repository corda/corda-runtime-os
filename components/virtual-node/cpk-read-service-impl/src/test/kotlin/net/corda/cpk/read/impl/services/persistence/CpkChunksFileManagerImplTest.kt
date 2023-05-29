package net.corda.cpk.read.impl.services.persistence

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.cpk.read.impl.Helpers
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl.Companion.toCpkDirName
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl.Companion.toCpkFileName
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManagerImpl.Companion.toFileName
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.core.toCorda
import net.corda.data.chunking.CpkChunkId
import net.corda.utilities.inputStream
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

class CpkChunksFileManagerImplTest {
    private lateinit var commonCpkCacheDir: Path
    private lateinit var cpkChunksFileManagerImpl: CpkChunksFileManagerImpl

    private lateinit var fs: FileSystem

    private companion object {
        const val DUMMY_HASH = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
    }

    @BeforeEach
    fun setUp() {
        val posix = Configuration.unix().toBuilder()
            .setAttributeViews("basic", "posix")
            .build()
        fs = Jimfs.newFileSystem(posix)
        commonCpkCacheDir = fs.getPath("/cpks").apply { Files.createDirectories(this) }
        cpkChunksFileManagerImpl = CpkChunksFileManagerImpl(commonCpkCacheDir)
    }

    @AfterEach
    fun cleanUp() {
        fs.close()
    }

    private fun CpkChunkId.toPath(): Path {
        val cpkXDir = commonCpkCacheDir.resolve(fs.getPath(this.cpkChecksum.toCorda().toCpkDirName()))
        if (!Files.isDirectory(cpkXDir)) {
            Files.createDirectory(cpkXDir)
        }
        val fileName = this.toFileName()
        return fs.getPath(cpkXDir.resolve(fileName).toString())
    }

    private fun CpkChunkId.toDummyFile(): Path {
        val filePath = toPath()
        Files.newByteChannel(filePath, WRITE, CREATE_NEW).use { channel ->
            channel.write(ByteBuffer.wrap(ByteArray(0)))
        }
        return filePath
    }

    private fun getCpkChunkContent(chunkId: CpkChunkId): ByteArray {
        val filePath = chunkId.toPath()
        val inStream = filePath.inputStream()
        return inStream.readAllBytes()
    }

    private fun getAssembledCpkContent(checksum: SecureHash): ByteArray {
        val filePath = commonCpkCacheDir.resolve(checksum.toCpkDirName()).resolve(checksum.toCpkFileName())
        val inStream = filePath.inputStream()
        return inStream.readAllBytes()
    }

    @Test
    fun `chunk file exists looks for requested cpk chunk id`() {
        val (cpkChunkId0, _) =
            Helpers.dummyCpkChunkIdToChunk(parseSecureHash(DUMMY_HASH), 0, parseSecureHash(DUMMY_HASH), byteArrayOf())
        cpkChunkId0.toDummyFile()
        assertTrue(cpkChunksFileManagerImpl.chunkFileExists(cpkChunkId0).exists)

        val (cpkChunkId1, _) =
            Helpers.dummyCpkChunkIdToChunk(parseSecureHash(DUMMY_HASH), 1, parseSecureHash(DUMMY_HASH), byteArrayOf())
        assertFalse(cpkChunksFileManagerImpl.chunkFileExists(cpkChunkId1).exists)
    }

    @Test
    fun `on write chunk file writes chunk file creates corresponding CPK directory`() {
        val (cpkChunkId, _) =
            Helpers.dummyCpkChunkIdToChunk(parseSecureHash(DUMMY_HASH), 0, parseSecureHash(DUMMY_HASH), byteArrayOf())
        val filePath = cpkChunkId.toDummyFile()
        assertTrue(filePath.contains(fs.getPath(parseSecureHash(DUMMY_HASH).toHexString())))
    }

    @Test
    fun `on write chunk file writes chunk file`() {
        val bytes = byteArrayOf(0x01, 0x02)
        val (cpkChunkId, chunk) =
            Helpers.dummyCpkChunkIdToChunk(parseSecureHash(DUMMY_HASH), 0, parseSecureHash(DUMMY_HASH), bytes)
        cpkChunksFileManagerImpl.writeChunkFile(cpkChunkId, chunk)
        assertThat(cpkChunkId.toPath()).isRegularFile

        val bytesRead = getCpkChunkContent(cpkChunkId)
        assertTrue(bytes.contentEquals(bytesRead))
    }

    @Test
    fun `assembles cpk`() {
        val cpkChecksum = parseSecureHash(DUMMY_HASH)
        val bytes0 = byteArrayOf(0x01, 0x02)
        val bytes1 = byteArrayOf(0x03, 0x04)
        val (cpkChunkId0, chunk0) =
            Helpers.dummyCpkChunkIdToChunk(cpkChecksum, 0, parseSecureHash(DUMMY_HASH), bytes0)
        val (cpkChunkId1, chunk1) =
            Helpers.dummyCpkChunkIdToChunk(cpkChecksum, 1, parseSecureHash(DUMMY_HASH), bytes1)

        cpkChunksFileManagerImpl.writeChunkFile(cpkChunkId0, chunk0)
        cpkChunksFileManagerImpl.writeChunkFile(cpkChunkId1, chunk1)
        cpkChunksFileManagerImpl.assembleCpk(cpkChecksum, sortedSetOf(cpkChunkId0, cpkChunkId1))
        assertTrue(byteArrayOf(0x01, 0x02, 0x03, 0x04).contentEquals(getAssembledCpkContent(cpkChecksum)))
    }
}