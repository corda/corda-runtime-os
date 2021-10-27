package net.corda.components.session.manager.dedup

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.session.RequestWindow
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DedupProcessor(
    dedupState: DedupState
) :
    StateAndEventProcessor<String, RequestWindow, Any> {

    private val maxSessionLength: Long = dedupState.maxSessionLength
    private val stateTopic: String = dedupState.stateTopic
    private val publisher: Publisher = dedupState.publisher
    private val executorService: ScheduledExecutorService = dedupState.executorService
    private val scheduledTasks: MutableMap<String, ScheduledFuture<*>> = dedupState.scheduledTasks

    companion object {
        private val log = contextLogger()
        private const val FLOW_EVENT_TOPIC = "FlowEventTopic"
    }

    override fun onNext(state: RequestWindow?, event: Record<String, Any>): StateAndEventProcessor.Response<RequestWindow> {
        val key = event.key
        val currentTime = System.currentTimeMillis()
        var responseState: RequestWindow? = null
        val responseEvents = mutableListOf<Record<*, *>>()

        val eventValue = event.value ?: return StateAndEventProcessor.Response(responseState, responseEvents)
        val timestamp = getEventTimeStamp(eventValue)
        val eventExpiryTime = timestamp + maxSessionLength
        val outputTopic = getEventOutputTopic(eventValue)

        if (state == null) {
            log.debug { "State is null for key $key" }
            if (eventExpiryTime > currentTime) {
                setSessionWindowForKey(key)

                log.debug { "Setting new state expiry time of $eventExpiryTime for key $key" }
                responseState = RequestWindow(event.key, eventExpiryTime)
                responseEvents.add(Record(outputTopic, getOutputEventKey(eventValue), eventValue))
            } else {
                log.debug { "Event with expiry time $eventExpiryTime has lapsed for key $key. Current time $currentTime" }
            }
        } else {
            val stateExpiryTime = state.expireTime
            log.debug { "State for key $key has expiry time of $stateExpiryTime" }
            if (stateExpiryTime > currentTime) {
                log.debug { "Duplicate message found for key $key. State expire time is within expiry limit of $stateExpiryTime" }
            } else {
                log.debug { "Resetting state for key $key. New expiry time of $eventExpiryTime for key $key" }
                responseState = RequestWindow(event.key, eventExpiryTime)
            }
        }

        return StateAndEventProcessor.Response(responseState, responseEvents)
    }

    private fun setSessionWindowForKey(key: String) {
        val scheduledTask = scheduledTasks[key]
        scheduledTask?.cancel(true)
        scheduledTasks[key] = executorService.schedule(
            { publisher.publish(listOf(Record(stateTopic, key, null))) },
            maxSessionLength,
            TimeUnit.MILLISECONDS
        )
    }


    private fun getOutputEventKey(event: Any): Any {
        return when (event) {
            is StartRPCFlow -> {
                FlowKey(event.clientId, event.rpcUsername)
            }
            else -> throw CordaMessageAPIFatalException("Unexpected type found")
        }
    }

    private fun getEventTimeStamp(eventValue: Any): Long {
        return when (eventValue) {
            is StartRPCFlow -> {
                eventValue.timestamp.epochSecond
            }
            else -> throw CordaMessageAPIFatalException("Unexpected type found")
        }
    }

    private fun getEventOutputTopic(eventValue: Any): String {
        return when (eventValue) {
            is StartRPCFlow -> {
                FLOW_EVENT_TOPIC
            }
            else -> throw CordaMessageAPIFatalException("Unexpected type found")
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<RequestWindow>
        get() = RequestWindow::class.java
    override val eventValueClass: Class<Any>
        get() = Any::class.java

}
