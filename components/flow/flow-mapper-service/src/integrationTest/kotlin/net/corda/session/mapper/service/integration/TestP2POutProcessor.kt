package net.corda.session.mapper.service.integration

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import org.junit.jupiter.api.fail
import java.util.concurrent.CountDownLatch

class TestP2POutProcessor(
    private val messageIdPrefix: String,
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int,
) : DurableProcessor<String, AppMessage> {

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java

    private var recordCount = 0

    override fun onNext(events: List<Record<String, AppMessage>>): List<Record<*, *>> {
        events.map {
            it.value?.message
        }.filterIsInstance<AuthenticatedMessage>()
            .map { it.header }.
            filter { it.messageId.startsWith(messageIdPrefix) }
            .forEach { _ ->
                recordCount++
                if (recordCount > expectedRecordCount) {
                    fail("Expected record count exceeded in events processed for this key")
                }
                latch.countDown()
            }

        return emptyList()
    }
}
