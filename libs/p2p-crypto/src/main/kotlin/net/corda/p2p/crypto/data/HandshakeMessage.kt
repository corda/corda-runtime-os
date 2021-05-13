package net.corda.p2p.crypto.data

data class HandshakeMessage(val recordHeader: CommonHeader, val encryptedData: ByteArray, val tag: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HandshakeMessage

        if (recordHeader != other.recordHeader) return false
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!tag.contentEquals(other.tag)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = recordHeader.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}
typealias ClientHandshakeMessage = HandshakeMessage
typealias ServerHandshakeMessage = HandshakeMessage