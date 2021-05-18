package net.corda.p2p.crypto.protocol.data

import net.corda.p2p.crypto.protocol.api.Mode

data class InitiatorHelloMessage(val commonHeader: CommonHeader, val initiatorPublicKey: ByteArray, val supportedModes: List<Mode>) {
    init {
        require(supportedModes.isNotEmpty()) { "There must be at least one supported mode." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InitiatorHelloMessage

        if (commonHeader != other.commonHeader) return false
        if (!initiatorPublicKey.contentEquals(other.initiatorPublicKey)) return false
        if (supportedModes != other.supportedModes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = commonHeader.hashCode()
        result = 31 * result + initiatorPublicKey.contentHashCode()
        result = 31 * result + supportedModes.hashCode()
        return result
    }

    fun toBytes(): ByteArray {
        return commonHeader.toBytes() +
                initiatorPublicKey +
                supportedModes.map { it.toString().toByteArray(Charsets.UTF_8) }.reduce { acc, bytes -> acc + bytes }
    }
}