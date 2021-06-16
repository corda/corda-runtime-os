package net.corda.components.examples.stateevent.processor

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import kotlin.system.exitProcess

class DemoStateAndEventProcessor(
    private val killProcessOnRecord: Int? = 0
) : StateAndEventProcessor<String, DemoRecord, DemoRecord> {

    private companion object {
        val log: Logger = contextLogger()
    }

    private var counter = 1

    private var expectedNextValues = mutableMapOf<String, Int>()

    override val keyClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<DemoRecord>
        get() = DemoRecord::class.java
    override val stateValueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(state: DemoRecord?, event: Record<String, DemoRecord>): StateAndEventProcessor.Response<DemoRecord> {
        if (counter == killProcessOnRecord) {
            log.error("Killing process for test purposes!")
            exitProcess(0)
        }

        val key = event.key
        val oldState = state?.value
        val eventRecord = event.value
        val eventRecordValue = eventRecord!!.value
        if (expectedNextValues[key] != null && expectedNextValues[key] != eventRecordValue) {
            log.error("Wrong record found!")
        }

        val updatedState = if (state != null) {
            DemoRecord(state.value + eventRecordValue)
        } else {
            DemoRecord(eventRecordValue)
        }

        log.info("Key: $key, Old State: $oldState, new event value: $eventRecordValue, new state value: ${updatedState.value}")
        expectedNextValues[key] = eventRecordValue + 1
        counter++
        return StateAndEventProcessor.Response(updatedState, emptyList())
    }

}