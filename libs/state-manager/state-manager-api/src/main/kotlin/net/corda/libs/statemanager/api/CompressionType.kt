package net.corda.libs.statemanager.api

import java.nio.charset.StandardCharsets

enum class CompressionType(val header: String) {
    SNAPPY("snappy"),
    NONE("none");

    fun getHeader(): ByteArray {
        val headerBytes = this.header.toByteArray(StandardCharsets.UTF_8)
        return headerBytes.copyInto(ByteArray(HEADER_SIZE))
    }

    companion object {

        const val HEADER_SIZE = 8

        fun fromHeader(headerBytes: ByteArray): CompressionType? {
            val headerBytesTrimmed = headerBytes.filterNot { it.toInt() == 0 }.toByteArray()
            val headerString = headerBytesTrimmed.toString(StandardCharsets.UTF_8)
            return values().find { it.header == headerString }
        }
    }
}
