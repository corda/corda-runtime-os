package net.corda.chunking.impl

import java.nio.ByteBuffer
import net.corda.chunking.Checksum
import net.corda.crypto.core.toAvro
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChunkBuilderServiceImplTest {

    private val chunkBuilderService = ChunkBuilderServiceImpl()

    private companion object {
        const val id = "id"
        const val chunkNumber = 1
        const val offset = 30L
        const val offsetIndex = "Offset"
        const val fileNameIndex = "FileName"
        const val fileName = "MyFileName"
        val chunkBytes = ByteBuffer.wrap("chunk".toByteArray())
    }

    private lateinit var properties: KeyValuePairList
    private val secureHash: SecureHash = Checksum.digestForBytes("bytes".toByteArray())

    @BeforeEach
    fun setup () {
        properties = KeyValuePairList.newBuilder().setItems(mutableListOf()).build()
        properties.items.add(KeyValuePair(fileNameIndex, fileName))
        properties.items.add(KeyValuePair(offsetIndex, offset.toString()))
    }

    @Test
    fun `test final chunk has secure hash and empty bytes`() {
        val chunk = chunkBuilderService.buildFinalChunk(id, chunkNumber, secureHash, offset, properties, fileName)
        assertThat(chunk.requestId).isEqualTo(id)
        assertThat(chunk.partNumber).isEqualTo(chunkNumber)
        assertThat(chunk.checksum).isEqualTo(secureHash.toAvro())
        assertThat(chunk.properties).isEqualTo(properties)
        assertThat(chunk.properties.items.find { it.key == fileNameIndex }?.value).isEqualTo(fileName)
        assertThat(chunk.properties.items.find { it.key == offsetIndex }?.value).isEqualTo(offset.toString())
        assertThat(chunk.fileName).isEqualTo(fileName)
        assertThat(chunk.offset).isEqualTo(offset)
        assertThat(chunk.data.array().size).isEqualTo(0)
    }

    @Test
    fun `test chunk has np secure hash and empty bytes`() {
        val chunk = chunkBuilderService.buildChunk(id, chunkNumber, chunkBytes, offset, properties, fileName)
        assertThat(chunk.requestId).isEqualTo(id)
        assertThat(chunk.partNumber).isEqualTo(chunkNumber)
        assertThat(chunk.data).isEqualTo(chunkBytes)
        assertThat(chunk.checksum).isNull()
        assertThat(chunk.properties).isEqualTo(properties)
        assertThat(chunk.properties.items.find { it.key == fileNameIndex }?.value).isEqualTo(fileName)
        assertThat(chunk.properties.items.find { it.key == offsetIndex }?.value).isEqualTo(offset.toString())
        assertThat(chunk.fileName).isEqualTo(fileName)
        assertThat(chunk.offset).isEqualTo(offset)
    }
}