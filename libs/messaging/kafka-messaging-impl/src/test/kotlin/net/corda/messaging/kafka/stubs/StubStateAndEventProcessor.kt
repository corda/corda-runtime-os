package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class StubStateAndEventProcessor(
    private val latch: CountDownLatch? = null,
    private val exception: Exception? = null
) : StateAndEventProcessor<String, String, String> {

    val inputs = mutableListOf<Pair<String?, Record<String, String>>>()
    override fun onNext(
        state: String?,
        event: Record<String, String>
    ): StateAndEventProcessor.Response<String> {
        latch?.countDown()

        if (exception != null) {
            throw exception
        }
        val outState = "state${latch?.count}"
        inputs.add(Pair(outState, event))
        return StateAndEventProcessor.Response(outState, listOf(Record(event.topic, event.key, "response to $state")))
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<String>
        get() = String::class.java
}
