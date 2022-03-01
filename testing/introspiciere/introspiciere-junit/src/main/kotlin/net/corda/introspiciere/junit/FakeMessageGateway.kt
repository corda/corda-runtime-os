package net.corda.introspiciere.junit

import net.corda.introspiciere.core.MessagesGateway
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.Msg
import java.time.Instant

class FakeMessageGateway : MessagesGateway {

    private val messages = mutableMapOf<String, MutableList<Msg>>()

    override fun readFrom(topic: String, schema: String, from: Long): Pair<List<Msg>, Long> {
        val timestamp = if (from < 0) now else from
        return messages[topic]!!.filter { it.timestamp >= timestamp } to timestamp
    }

    override fun send(topic: String, message: KafkaMessage) {
        messages.getOrPut(topic) { mutableListOf() }.add(Msg(
            now,
            key = message.key,
            data = message.schema
        ))
    }

    private val now: Long
        get() = Instant.now().toEpochMilli()
}