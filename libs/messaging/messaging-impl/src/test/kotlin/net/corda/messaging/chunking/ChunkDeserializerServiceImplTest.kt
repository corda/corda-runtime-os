package net.corda.messaging.chunking

import java.nio.ByteBuffer
import net.corda.chunking.Checksum
import net.corda.chunking.impl.ChunkBuilderServiceImpl
import net.corda.chunking.toAvro
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChunkDeserializerServiceImplTest {
    private lateinit var valueDeserializer: CordaAvroDeserializer<String>
    private lateinit var keyDeserializer: CordaAvroDeserializer<String>
    private lateinit var chunkDeserializerService: ChunkDeserializerServiceImpl<String, String>
    private lateinit var platformDigestService: PlatformDigestService

    private val chunkBuilder = ChunkBuilderServiceImpl()
    private val id = "id"
    private val realKey = "realKey"
    private val realKeyBytes = realKey.toByteArray()
    private val testChunkKey1 = ChunkKey(id, ByteBuffer.wrap(realKeyBytes), 1)
    private val testChunkKey2 = ChunkKey(id, ByteBuffer.wrap(realKeyBytes), 2)
    private val testFinalChunkKey = ChunkKey(id, ByteBuffer.wrap(realKeyBytes), 3)
    private val firstChunkValue = "first"
    private val secondChunkValue = "second"
    private val completeValue = firstChunkValue + secondChunkValue
    private val bytes1 = firstChunkValue.toByteArray()
    private val bytes2 = secondChunkValue.toByteArray()
    private val fullBytes = bytes1 + bytes2

    private lateinit var testChunk1: Chunk
    private lateinit var testChunk2: Chunk
    private lateinit var testFinalChunk: Chunk
    private lateinit var chunkMap : MutableMap<ChunkKey, Chunk>
    private lateinit var chunks : MutableList<Chunk>

    @BeforeEach
    fun setup() {
        valueDeserializer = mock()
        keyDeserializer = mock()
        platformDigestService = mock()
        chunkDeserializerService = ChunkDeserializerServiceImpl(keyDeserializer, valueDeserializer, { }, platformDigestService)
        whenever(keyDeserializer.deserialize(realKeyBytes)).thenReturn(realKey)
        whenever(valueDeserializer.deserialize(fullBytes)).thenReturn(completeValue)

        testChunk1 = chunkBuilder.buildChunk(id, 1,  ByteBuffer.wrap(bytes1), 10)
        testChunk2 = chunkBuilder.buildChunk(id, 2,  ByteBuffer.wrap(bytes2), 20)
        testFinalChunk = chunkBuilder.buildFinalChunk(id, 3,  Checksum.digestForBytes(fullBytes), 20)
        chunks = mutableListOf(testChunk1, testChunk2, testFinalChunk)
        chunkMap = mutableMapOf(
            testChunkKey1 to testChunk1,
            testChunkKey2 to testChunk2,
            testFinalChunkKey to testFinalChunk
        )
    }

    @Test
    fun `assemble chunks with keys fails due to key deserialization error`() {
        whenever(keyDeserializer.deserialize(any())).thenReturn(null)
        val result = chunkDeserializerService.assembleChunks(chunkMap)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `assemble chunks with keys fails due to value deserialization error`() {
        whenever(valueDeserializer.deserialize(any())).thenReturn(null)
        val result = chunkDeserializerService.assembleChunks(chunkMap)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `assemble chunks with keys success`() {
        val result = chunkDeserializerService.assembleChunks(chunkMap)
        assertThat(result?.first).isEqualTo(realKey)
        assertThat(result?.second).isEqualTo(completeValue)
    }

    @Test
    fun `assemble chunks with keys fails due to missing sever hash`() {
        testFinalChunk.checksum = null
        val result = chunkDeserializerService.assembleChunks(chunkMap)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `assemble chunks with keys fails due to incorrect sever hash`() {
        testFinalChunk.checksum = Checksum.digestForBytes("somewrongbytes".toByteArray()).toAvro()
        val result = chunkDeserializerService.assembleChunks(chunkMap)
        assertThat(result).isEqualTo(null)
    }

    //assemble without keys
    @Test
    fun `assemble chunks fails due to value deserialization error`() {
        whenever(valueDeserializer.deserialize(any())).thenReturn(null)
        val result = chunkDeserializerService.assembleChunks(chunks)
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `assemble chunks success`() {
        val result = chunkDeserializerService.assembleChunks(chunks)
        assertThat(result).isEqualTo(completeValue)
    }

    @Test
    fun `assemble chunks fails due to missing sever hash`() {
        testFinalChunk.checksum = null
        val result = chunkDeserializerService.assembleChunks(chunks)
        assertThat(result).isEqualTo(null)
    }
}
