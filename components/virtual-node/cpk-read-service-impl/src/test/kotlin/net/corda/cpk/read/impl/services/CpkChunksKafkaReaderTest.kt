package net.corda.cpk.read.impl.services

import net.corda.cpk.read.impl.Helpers
import net.corda.cpk.read.impl.services.persistence.CpkChunkFileLookUp
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManager
import net.corda.crypto.core.parseSecureHash
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CpkChunksKafkaReaderTest {
    private lateinit var cpkChunksFileManager: CpkChunksFileManager

    private companion object {
        const val DUMMY_HASH = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
    }

    @BeforeEach
    fun setUp() {
        cpkChunksFileManager = mock()
        val cpkChunkFileLookUp = mock<CpkChunkFileLookUp>()
        whenever(cpkChunkFileLookUp.exists).thenReturn(false)
        whenever(cpkChunksFileManager.chunkFileExists(any())).thenReturn(cpkChunkFileLookUp)
    }

    @Test
    fun `on writing CPK chunks writes them to disk cache`() {
        val checksum = parseSecureHash(DUMMY_HASH)
        val cpkChunksKafkaReader = CpkChunksKafkaReader(mock(), cpkChunksFileManager, mock())

        val (cpkChunkId0, chunk0) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 0, checksum, byteArrayOf(0x01, 0x02))
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId0, chunk0)
        verify(cpkChunksFileManager).writeChunkFile(cpkChunkId0, chunk0)
        assertTrue(cpkChunksKafkaReader.receivedCpkChunksCache.getChunkIds(checksum)!!.size == 1)

        val (cpkChunkId1, chunk1) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 1, checksum, byteArrayOf(0x03, 0x04))
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId1, chunk1)
        verify(cpkChunksFileManager).writeChunkFile(cpkChunkId1, chunk1)
        assertTrue(cpkChunksKafkaReader.receivedCpkChunksCache.getChunkIds(checksum)!!.size == 2)
        assertFalse(cpkChunksKafkaReader.receivedCpkChunksCache.allChunksReceived(checksum))
    }

    @Test
    fun `on receiving all CPK chunks assembles CPK and clears received chunks cache for that CPK`() {
        val checksum = parseSecureHash(DUMMY_HASH)
        val cpkChunksKafkaReader = CpkChunksKafkaReader(mock(), cpkChunksFileManager, mock())

        val (cpkChunkId0, chunk0) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 0, checksum, byteArrayOf(0x01, 0x02))
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId0, chunk0)
        verify(cpkChunksFileManager).writeChunkFile(cpkChunkId0, chunk0)
        assertTrue(cpkChunksKafkaReader.receivedCpkChunksCache.getChunkIds(checksum)!!.size == 1)

        val (cpkChunkId1, chunk1) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 1, checksum, byteArrayOf())
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId1, chunk1)
        verify(cpkChunksFileManager).writeChunkFile(cpkChunkId1, chunk1)

        verify(cpkChunksFileManager).assembleCpk(any(), any())
        assertNull(cpkChunksKafkaReader.receivedCpkChunksCache.getChunkIds(checksum))
    }
}