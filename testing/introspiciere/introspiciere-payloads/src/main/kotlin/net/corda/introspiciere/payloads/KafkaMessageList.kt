package net.corda.introspiciere.payloads

import net.corda.introspiciere.domain.KafkaMessage

/**
 * Annoyingly gson has problem deserializing lists directly.
 */
data class KafkaMessageList(val messages: List<KafkaMessage>)