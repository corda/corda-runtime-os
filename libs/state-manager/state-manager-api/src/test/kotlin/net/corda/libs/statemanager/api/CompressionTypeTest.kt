package net.corda.libs.statemanager.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompressionTypeTest {

    @Test
    fun `Test CompressionType from header as bytes`() {
        val compressionType = CompressionType.SNAPPY
        val bytes = compressionType.getHeader()
        val compressionTypeResult = CompressionType.fromHeader(bytes)
        assertThat(compressionTypeResult).isEqualTo(compressionType)
    }
}