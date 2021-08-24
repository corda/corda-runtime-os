package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

class StubStateAndEventProcessor(
    var latch: CountDownLatch? = null,
    private val exceptionOnFirstCall: Exception? = null
) : StateAndEventProcessor<String, String, ByteBuffer> {

    var exceptionThrown = false
    val inputs = mutableListOf<Pair<String?, Record<String, ByteBuffer>>>()
    override fun onNext(
        state: String?,
        event: Record<String, ByteBuffer>
    ): StateAndEventProcessor.Response<String> {
        if (!exceptionThrown && exceptionOnFirstCall != null) {
            exceptionThrown = true
            throw exceptionOnFirstCall
        }
        latch?.countDown()

        val outState = "state${latch?.count}"
        inputs.add(Pair(outState, event))
        return StateAndEventProcessor.Response(outState, listOf(Record(event.topic, event.key, "response to $state")))
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<ByteBuffer>
        get() = ByteBuffer::class.java
}
