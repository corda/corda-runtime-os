package net.corda.messaging.kafka.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.data.demo.DemoStateRecord
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestStateEventProcessor(
    private val onNextLatch: CountDownLatch,
    private val updateState: Boolean,
    private val throwException: Boolean = false,
    private val outputTopic: String? = null,
) :
    StateAndEventProcessor<String,
            DemoStateRecord,
            DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<DemoStateRecord>
        get() = DemoStateRecord::class.java
    override val eventValueClass: Class<DemoRecord>
        get() = DemoRecord::class.java


    private var exceptionThrown = false

    override fun onNext(state: DemoStateRecord?, event: Record<String, DemoRecord>): Response<DemoStateRecord> {
        onNextLatch.countDown()

        if (throwException && !exceptionThrown) {
            exceptionThrown = true
            throw CordaMessageAPIIntermittentException("Test exception")
        }

        val newState = if (updateState) {
            val newStateValue = if (event.value == null) 1 else (event.value!!.value + 1)
            DemoStateRecord(newStateValue)
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
