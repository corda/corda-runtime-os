package net.corda.introspiciere.junit

import net.corda.introspiciere.core.MessagesGateway
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.Msg
import java.time.Duration
import java.time.Instant

class FakeMessageGateway : MessagesGateway {

    private val messages = mutableMapOf<String, MutableList<Msg>>()

    override fun readFromEnd(topic: String, schema: String, timeout: Duration?): Pair<List<Msg>, Long> {
        val nowTimestamp = now
        val msgs = messages[topic]!!.filter { it.timestamp >= nowTimestamp }
        return msgs to (msgs.maxOfOrNull { it.timestamp + 1 } ?: nowTimestamp)
    }

    override fun readFromBeginning(topic: String, schema: String, timeout: Duration?): Pair<List<Msg>, Long> {
        val msgs = messages[topic].orEmpty()
        return msgs to (msgs.maxOfOrNull { it.timestamp + 1 } ?: 0L)
    }

    override fun readFrom(topic: String, schema: String, from: Long, timeout: Duration?): Pair<List<Msg>, Long> {
        val msgs = messages[topic].orEmpty().filter { it.timestamp >= from }
        return msgs to (msgs.maxOfOrNull { it.timestamp + 1 } ?: from)
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