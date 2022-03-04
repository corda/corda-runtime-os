package net.corda.introspiciere.server.fakes

import net.corda.introspiciere.core.MessagesGateway
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.Msg
import java.time.Duration
import java.time.Instant

class FakeMessageGateway : MessagesGateway {

    private val messages = mutableMapOf<String, MutableList<Msg>>()

    override fun readFrom(topic: String, schema: String, from: Long, timeout: Duration): Pair<List<Msg>, Long> {
        val msgs = messages[topic].orEmpty().filter { it.timestamp >= from }
        val maxTimestamp = msgs.maxOfOrNull { it.timestamp } ?: from
        return msgs to maxTimestamp
    }

    override fun send(topic: String, message: KafkaMessage) {
        val msg = Msg(
            timestamp = Instant.now().toEpochMilli(),
            key = message.key,
            data = message.schema)
        messages.getOrPut(topic) { mutableListOf() } += msg
    }
}