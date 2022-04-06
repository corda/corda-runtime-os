package net.corda.messaging.integration.processors

import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestStateEventProcessorStrings(
    private val onNextLatch: CountDownLatch,
    private val updateState: Boolean,
    private var throwExceptionOnFirst: Boolean = false,
    private val outputTopic: String? = null,
    private var delayProcessorOnFirst: Long? = null
) :
    StateAndEventProcessor<String,
            String,
            String> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<String>
        get() = String::class.java

    override fun onNext(state: String?, event: Record<String, String>): Response<String> {
        onNextLatch.countDown()

        if (delayProcessorOnFirst != null) {
            Thread.sleep(delayProcessorOnFirst!!)
            delayProcessorOnFirst = null
        }

        if (throwExceptionOnFirst) {
            throwExceptionOnFirst = false
            throw CordaMessageAPIIntermittentException("Test exception")
        }

        val newState = if (updateState) {
            event.value
        } else {
            null
        }

        val outputRecordList = if (outputTopic != null) {
            listOf(Record(outputTopic, event.key, event.value))
        } else {
            emptyList()
        }

        return Response(newState, outputRecordList)
    }
}
