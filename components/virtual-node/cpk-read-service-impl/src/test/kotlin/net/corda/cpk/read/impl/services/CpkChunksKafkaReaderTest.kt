package net.corda.cpk.read.impl.services

import net.corda.cpk.read.impl.TestUtils.dummyCpkChunkIdToChunk
import net.corda.cpk.read.impl.services.persistence.CpkChunkFileLookUp
import net.corda.cpk.read.impl.services.persistence.CpkChunksFileManager
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CpkChunksKafkaReaderTest {
    lateinit var cpkChunksKafkaReader: CpkChunksKafkaReader
    lateinit var cpkChunksFileManager: CpkChunksFileManager

    private companion object {
        const val DUMMY_HASH = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
    }

    @BeforeEach
    fun setUp() {
        cpkChunksFileManager = mock()
        val cpkChunkFileLookUp = mock<CpkChunkFileLookUp>()
        whenever(cpkChunkFileLookUp.exists).thenReturn(true)
        whenever(cpkChunksFileManager.chunkFileExists(any())).thenReturn(cpkChunkFileLookUp)
    }

    @Test
    fun `on writing CPK chunk files holds chunks ids to received chunks cache`() {
        val checksum = SecureHash.create(DUMMY_HASH)
        cpkChunksKafkaReader = CpkChunksKafkaReader(mock(), cpkChunksFileManager, mock())

        val (cpkChunkId0, chunk0) =
            dummyCpkChunkIdToChunk(checksum, 0, checksum, byteArrayOf(0x01, 0x02))
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId0, chunk0)
        assertTrue(cpkChunksKafkaReader.chunksReceivedPerCpk[checksum]!!.chunks.size == 1)

        val (cpkChunkId1, chunk1) =
            dummyCpkChunkIdToChunk(checksum, 1, checksum, byteArrayOf(0x03, 0x04))
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId1, chunk1)
        assertTrue(cpkChunksKafkaReader.chunksReceivedPerCpk[checksum]!!.chunks.size == 2)
    }

    @Test
    fun `on receiving all CPK chunks assembles CPK and clears received chunks cache for that CPK`() {
        val checksum = SecureHash.create(DUMMY_HASH)
        cpkChunksKafkaReader = CpkChunksKafkaReader(mock(), cpkChunksFileManager, mock())

        val (cpkChunkId0, chunk0) =
            dummyCpkChunkIdToChunk(checksum, 0, checksum, byteArrayOf(0x01, 0x02))
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId0, chunk0)
        assertTrue(cpkChunksKafkaReader.chunksReceivedPerCpk[checksum]!!.chunks.size == 1)

        val (cpkChunkId1, chunk1) =
            dummyCpkChunkIdToChunk(checksum, 1, checksum, byteArrayOf())
        cpkChunksKafkaReader.writeChunkFile(cpkChunkId1, chunk1)
        verify(cpkChunksFileManager).assembleCpk(any(), any())
        assertNull(cpkChunksKafkaReader.chunksReceivedPerCpk[checksum])
    }
}