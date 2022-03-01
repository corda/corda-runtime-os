package net.corda.introspiciere.core

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.Msg

interface MessagesGateway {
    fun readFrom(topic: String, schema: String, from: Long): Pair<List<Msg>, Long>
    fun send(topic: String, message: KafkaMessage)
}

class MessagesGatewaysImpl : MessagesGateway {
    override fun readFrom(topic: String, schema: String, from: Long): Pair<List<Msg>, Long> {
        TODO("Not yet implemented")
    }

    override fun send(topic: String, message: KafkaMessage) {
        TODO("Not yet implemented")
    }
}