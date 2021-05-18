package net.corda.p2p.crypto.protocol.data

data class CommonHeader(val messageType: MessageType,
                        val protocolVersion: Int,
                        val sessionId: String,
                        val sequenceNo: Long,
                        val timestamp: Long) {
    fun toBytes(): ByteArray {
        return messageType.toString().toByteArray(Charsets.UTF_8) +
                protocolVersion.toByte() +
                sessionId.toByteArray(Charsets.UTF_8) +
                sequenceNo.toByte() +
                timestamp.toByte()
    }
}