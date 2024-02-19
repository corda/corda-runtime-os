package net.corda.libs.statemanager.api

import java.nio.charset.StandardCharsets

enum class CompressionType(val header: String) {
    SNAPPY("snap"),
    NONE("none");

    fun getHeader(): ByteArray {
        val headerBytes = this.header.toByteArray(StandardCharsets.UTF_8)
        return headerBytes.copyInto(ByteArray(4))
    }
}