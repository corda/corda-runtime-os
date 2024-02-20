package net.corda.libs.statemanager.impl.compression.impl

import net.corda.libs.statemanager.api.CompressionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CompressionServiceImplTest {
    private val someBytes = "some bytes longer than 8".toByteArray()
    private val shortBytes = "bytes".toByteArray()
    private val compressionService = CompressionServiceImpl()

    @Test
    fun `write bytes with no compression`() {
        assertEquals(someBytes, compressionService.writeBytes(someBytes))
    }

    @Test
    fun `write bytes with SNAPPY compression`() {
        val compressed = compressionService.writeBytes(someBytes, CompressionType.SNAPPY)
        assertNotEquals(compressed.size, someBytes.size)
        assertThat(CompressionType.fromHeader(compressed.copyOfRange(0, CompressionType.HEADER_SIZE))).isEqualTo(CompressionType.SNAPPY)
    }

    @Test
    fun `read bytes with no compression`() {
        assertContentEquals(someBytes, compressionService.readBytes(someBytes))
    }

    @Test
    fun `read short bytes with no compression`() {
        assertContentEquals(shortBytes, compressionService.readBytes(shortBytes))
    }

    @Test
    fun `read bytes with SNAPPY compression`() {
        val compressed = compressionService.writeBytes(someBytes, CompressionType.SNAPPY)
        assertContentEquals(someBytes, compressionService.readBytes(compressed))
    }
}