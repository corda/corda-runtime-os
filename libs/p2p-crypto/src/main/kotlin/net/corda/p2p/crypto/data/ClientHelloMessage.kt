package net.corda.p2p.crypto.data

import net.corda.p2p.crypto.Mode

data class ClientHelloMessage(val commonHeader: CommonHeader, val clientPublicKey: ByteArray, val supportedModes: List<Mode>) {
    init {
        require(supportedModes.isNotEmpty()) { "There must be at least one supported mode." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClientHelloMessage

        if (commonHeader != other.commonHeader) return false
        if (!clientPublicKey.contentEquals(other.clientPublicKey)) return false
        if (supportedModes != other.supportedModes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = commonHeader.hashCode()
        result = 31 * result + clientPublicKey.contentHashCode()
        result = 31 * result + supportedModes.hashCode()
        return result
    }

    fun toBytes(): ByteArray {
        return commonHeader.toBytes() +
                clientPublicKey +
                supportedModes.map { it.toString().toByteArray(Charsets.UTF_8) }.reduce { acc, bytes -> acc + bytes }
    }
}