package net.corda.p2p.crypto.data

import net.corda.p2p.crypto.Mode

data class ResponderHelloMessage(val commonHeader: CommonHeader, val responderPublicKey: ByteArray, val selectedMode: Mode) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResponderHelloMessage

        if (commonHeader != other.commonHeader) return false
        if (!responderPublicKey.contentEquals(other.responderPublicKey)) return false
        if (selectedMode != other.selectedMode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = commonHeader.hashCode()
        result = 31 * result + responderPublicKey.contentHashCode()
        result = 31 * result + selectedMode.hashCode()
        return result
    }

    fun toBytes(): ByteArray {
        return commonHeader.toBytes() +
                responderPublicKey +
                selectedMode.toString().toByteArray(Charsets.UTF_8)
    }
}