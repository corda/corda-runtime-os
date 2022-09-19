package net.corda.persistence.common

import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer

class PayloadChecker(private val maxPayloadSize: Int) {
    companion object {
        private val log = contextLogger()
    }

    fun checkSize(
        bytes: ByteBuffer
    ): ByteBuffer {
        val kb = bytes.array().size / 1024
        if (bytes.array().size > maxPayloadSize) {
            throw KafkaMessageSizeException("Payload $kb kb, exceeds max Kafka payload size ${maxPayloadSize / (1024)} kb")
        }
        if(log.isDebugEnabled)
            log.debug("Payload $kb kb < max Kafka payload size ${maxPayloadSize / (1024)} kb")
        return bytes
    }
}