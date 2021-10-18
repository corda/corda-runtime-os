package net.corda.components.examples.stateevent.processor

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class ChaosTestStringsStateAndEventProcessor(
    private val killProcessOnRecord: Int? = 0,
    private val delayOnNext: Long = 0
) : StateAndEventProcessor<String, String, String> {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
        const val outputTopic = "StateEventOutputTopic"
    }

    private var counter = 1
    private var expectedNextValues = mutableMapOf<String, Int>()

    override val keyClass: Class<String>
        get() = String::class.java
    override val eventValueClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<String>
        get() = String::class.java

    override fun onNext(state: String?, event: Record<String, String>): StateAndEventProcessor.Response<String> {
        if (counter == killProcessOnRecord) {
            log.info("Killing process for test purposes!")
            exitProcess(0)
        }

        if (delayOnNext != 0L) {
            log.info("State and event processor pausing..")
            Thread.sleep(delayOnNext)
        }

        val key = event.key
        val oldState = "$state"
        val eventRecord = event
        val eventRecordValue = eventRecord

        val updatedState = if (state != null) {
            "$state::$eventRecordValue"
        } else {
            "s::$eventRecordValue"
        }

        val responseEvent = Record(outputTopic, key, updatedState)

        log.info("Key: $key, Old State: $oldState, new event value: $eventRecordValue, new state value: $updatedState")
        counter++
        return StateAndEventProcessor.Response(updatedState, listOf(responseEvent))
    }
}
