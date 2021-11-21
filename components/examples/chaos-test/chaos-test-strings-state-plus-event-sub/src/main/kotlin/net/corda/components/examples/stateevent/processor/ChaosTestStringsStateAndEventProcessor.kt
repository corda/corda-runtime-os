package net.corda.components.examples.stateevent.processor

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import kotlin.system.exitProcess

class ChaosTestStringsStateAndEventProcessor(
    private val killProcessOnRecord: Int? = 0,
    private val delayOnNext: Long = 0
) : StateAndEventProcessor<String, String, String> {

    private companion object {
        val log: Logger = contextLogger()
        const val outputTopic = "StateEventOutputTopic"
    }

    private var counter = 1

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

        //
        val key = event.key
        val oldState = "$state"
        val updatedState = updateState(state, event.toString())

        val responseEvent = Record(outputTopic, key, updatedState)
        log.info("Key: $key, Old State: $oldState, new event value: $event, new state value: $updatedState")
        counter++
        return StateAndEventProcessor.Response(updatedState, listOf(responseEvent))
    }

    private fun updateState(state: String?, eventRecordValue: String): String{
        val reStr = """state-event-state\(([0-9]+)\)"""
        val re = reStr.toRegex()

        return if (state != null) {
            "state-event-state(${re.find(state)!!.groups[1]!!.value.toInt()+1})::$eventRecordValue"
        } else {
            "state-event-state(0)::$eventRecordValue"
        }
    }

}