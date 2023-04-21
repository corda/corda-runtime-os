package net.corda.persistence.common

import net.corda.libs.configuration.SmartConfig
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.schema.configuration.MessagingConfig
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class PayloadChecker(private val maxPayloadSize: Int) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val CORDA_MESSAGE_OVERHEAD = 1024
    }

    constructor(config: SmartConfig):
            // max allowed msg size minus headroom for wrapper message
            this(config.getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE) - CORDA_MESSAGE_OVERHEAD)

    fun checkSize(
        bytes: ByteBuffer
    ): ByteBuffer {
        val kb = bytes.array().size / 1024
        if (bytes.array().size > maxPayloadSize) {
            throw KafkaMessageSizeException("Payload $kb kb, exceeds max Kafka payload size ${maxPayloadSize / (1024)} kb")
        }
        if (log.isDebugEnabled) {
            log.debug("Payload {} kb < max Kafka payload size {} kb", kb, maxPayloadSize / 1024)
        }
        return bytes
    }
}