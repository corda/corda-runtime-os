package net.corda.components.examples.stateevent.processor

import net.corda.data.demo.DemoRecord
import net.corda.data.demo.DemoStateRecord
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class DemoStateAndEventProcessor(
    private val killProcessOnRecord: Int? = 0,
    private val delayOnNext: Long = 0
) : StateAndEventProcessor<String, DemoStateRecord, DemoRecord> {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val outputTopic = "StateEventOutputTopic"
    }

    private var counter = 1
    private var expectedNextValues = mutableMapOf<String, Int>()

    override val keyClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<DemoRecord>
        get() = DemoRecord::class.java
    override val stateValueClass: Class<DemoStateRecord>
        get() = DemoStateRecord::class.java

    override fun onNext(state: DemoStateRecord?, event: Record<String, DemoRecord>): StateAndEventProcessor.Response<DemoStateRecord> {
        if (counter == killProcessOnRecord) {
            log.error("Killing process for test purposes!")
            exitProcess(0)
        }

        if (delayOnNext != 0L) {
            log.error("State and event processor pausing..")
            Thread.sleep(delayOnNext)
        }

        val key = event.key
        val oldState = state?.value
        val eventRecord = event.value
        val eventRecordValue = eventRecord!!.value
        val newPublisherSet = eventRecordValue == 1
        if (expectedNextValues[key] != null && expectedNextValues[key] != eventRecordValue && !newPublisherSet) {
            log.error("Wrong record found! Expected to find ${expectedNextValues[key]} but found $eventRecordValue")
            consoleLogger.info("Wrong record received by StateAndEvent processor! " +
                    "Expected to find ${expectedNextValues[key]} but found $eventRecordValue")
        }
        expectedNextValues[key] = eventRecordValue + 1

        val updatedState = if (state != null) {
            DemoStateRecord(state.value + eventRecordValue)
        } else {
            DemoStateRecord(eventRecordValue)
        }

        val responseEvent = Record(outputTopic, key, DemoRecord(updatedState.value))

        log.info("Key: $key, Old State: $oldState, new event value: $eventRecordValue, new state value: ${updatedState.value}")
        counter++
        return StateAndEventProcessor.Response(updatedState, listOf(responseEvent))
    }
}
