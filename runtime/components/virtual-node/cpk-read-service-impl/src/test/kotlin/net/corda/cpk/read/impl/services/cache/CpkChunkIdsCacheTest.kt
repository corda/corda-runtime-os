package net.corda.cpk.read.impl.services.cache

import net.corda.cpk.read.impl.Helpers
import net.corda.crypto.core.parseSecureHash
import net.corda.data.chunking.Chunk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CpkChunkIdsCacheTest {
    private lateinit var cpkChunkIdsCache: CpkChunkIdsCache

    private companion object {
        const val DUMMY_HASH = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"

        fun isZeroChunk(chunk: Chunk) =
            chunk.data.limit() == 0
    }

    @BeforeEach
    fun setUp() {
        cpkChunkIdsCache = CpkChunkIdsCache.Impl()
    }

    @Test
    fun `on non zero CPK chunk adds its chunk Id it to cache`() {
        val checksum = parseSecureHash(DUMMY_HASH)

        val (cpkChunkId0, chunk0) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 0, checksum, byteArrayOf(0x01, 0x02))
        cpkChunkIdsCache.addOrSetExpected(checksum, cpkChunkId0, isZeroChunk(chunk0))
        assertTrue(cpkChunkIdsCache.getChunkIds(checksum)!!.size == 1)

        val (cpkChunkId1, chunk1) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 1, checksum, byteArrayOf(0x03, 0x04))
        cpkChunkIdsCache.addOrSetExpected(checksum, cpkChunkId1, isZeroChunk(chunk1))
        assertTrue(cpkChunkIdsCache.getChunkIds(checksum)!!.size == 2)
        assertFalse(cpkChunkIdsCache.allChunksReceived(checksum))
    }

    @Test
    fun `on zero CPK chunk sets expected chunks count`() {
        val checksum = parseSecureHash(DUMMY_HASH)

        val (cpkChunkId0, chunk0) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 0, checksum, byteArrayOf(0x01, 0x02))
        cpkChunkIdsCache.addOrSetExpected(checksum, cpkChunkId0, isZeroChunk(chunk0))
        assertTrue(cpkChunkIdsCache.getChunkIds(checksum)!!.size == 1)

        val (cpkChunkId1, chunk1) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 1, checksum, byteArrayOf(0x03, 0x04))
        cpkChunkIdsCache.addOrSetExpected(checksum, cpkChunkId1, isZeroChunk(chunk1))
        assertTrue(cpkChunkIdsCache.getChunkIds(checksum)!!.size == 2)

        val (cpkChunkId2, chunk2) =
            Helpers.dummyCpkChunkIdToChunk(checksum, 2, checksum, byteArrayOf())
        cpkChunkIdsCache.addOrSetExpected(checksum, cpkChunkId2, isZeroChunk(chunk2))
        assertTrue(cpkChunkIdsCache.getChunkIds(checksum)!!.size == 2)
        assertTrue(cpkChunkIdsCache.allChunksReceived(checksum))
    }
}