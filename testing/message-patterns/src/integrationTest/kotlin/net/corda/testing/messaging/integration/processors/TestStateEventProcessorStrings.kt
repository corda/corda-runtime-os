package net.corda.testing.messaging.integration.processors

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
import net.corda.messaging.api.processor.StateAndEventProcessor.State
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

@Suppress("LongParameterList")
class TestStateEventProcessorStrings(
    private val onNextLatch: CountDownLatch,
    private val updateState: Boolean,
    private var throwIntermittentExceptionOnItem: Int = -1,
    private val outputTopic: String? = null,
    private var delayProcessorOnFirst: Long? = null,
    private var throwFatalExceptionOnItem: Int = -1,

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

    private var counter = 0

    override fun onNext(
        state: State<String>?, event: Record<String, String>
    ): Response<String> {
        counter++
        onNextLatch.countDown()

        if (delayProcessorOnFirst != null) {
            Thread.sleep(delayProcessorOnFirst!!)
            delayProcessorOnFirst = null
        }

        if (throwIntermittentExceptionOnItem == counter) {
            throw CordaMessageAPIIntermittentException("Test Intermittent exception")
        }

        if (throwFatalExceptionOnItem == counter) {
            throw CordaMessageAPIFatalException("Test Fatal exception")
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

        return Response(
            State(newState, metadata = null),
            outputRecordList
        )
    }
}
