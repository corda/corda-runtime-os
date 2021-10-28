package net.corda.components.session.manager.dedup

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.session.RequestWindow
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
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
        log.info("Received event $event with state $state")
        val key = event.key
        val currentTime = System.currentTimeMillis()
        var responseState: RequestWindow? = state
        val responseEvents = mutableListOf<Record<*, *>>()

        val eventValue = event.value ?: return StateAndEventProcessor.Response(responseState, responseEvents)
        val timestamp = getEventTimeStamp(eventValue)
        val eventExpiryTime = timestamp + maxSessionLength
        val outputTopic = getEventOutputTopic(eventValue)

        if (state == null) {
            log.info("State is null for key $key")
            if (eventExpiryTime > currentTime) {
                setSessionWindowForKey(key)

                log.info("Valid message. Setting new state expiry time of $eventExpiryTime for key $key")
                responseState = RequestWindow(event.key, eventExpiryTime)
                responseEvents.add(Record(outputTopic, getOutputEventKey(eventValue), eventValue))
            } else {
                log.info("Dropping Message. Event with expiry time $eventExpiryTime has lapsed for key $key. Current time $currentTime")
            }
        } else {
            val stateExpiryTime = state.expireTime
            log.info("State for key $key has expiry time of $stateExpiryTime")
            if (stateExpiryTime > currentTime) {
                log.info("Duplicate message found for key $key. State expire time is within expiry limit of $stateExpiryTime")
            } else {
                log.info("Valid message. Resetting state for key $key. New expiry time of $eventExpiryTime for key $key")
                responseState = RequestWindow(event.key, eventExpiryTime)
                responseEvents.add(Record(outputTopic, getOutputEventKey(eventValue), eventValue))
            }
        }

        return StateAndEventProcessor.Response(responseState, responseEvents)
    }

    private fun setSessionWindowForKey(key: String) {
        val scheduledTask = scheduledTasks[key]
        scheduledTask?.cancel(true)
        scheduledTasks[key] = executorService.schedule(
            {
                log.info("Clearing up expired state from processor for key $key")
                publisher.publish(listOf(Record(stateTopic, key, null)))
            },
            maxSessionLength,
            TimeUnit.MILLISECONDS
        )
    }

    private fun getOutputEventKey(event: Any): Any {
        return when (event) {
            is FlowEvent -> {
                when (val payload = event.payload) {
                    is StartRPCFlow -> {
                        FlowKey(payload.clientId, payload.rpcUsername)
                    }
                    else -> throw CordaMessageAPIFatalException("Unexpected type found")
                }
            }
            else -> throw CordaMessageAPIFatalException("Unexpected type found")
        }
    }

    private fun getEventTimeStamp(eventValue: Any): Long {
        return when (eventValue) {
            is FlowEvent -> {
                when (val payload = eventValue.payload) {
                    is StartRPCFlow -> {
                        payload.timestamp.toEpochMilli()
                    }
                    else -> throw CordaMessageAPIFatalException("Unexpected type found")
                }
            }
            else -> throw CordaMessageAPIFatalException("Unexpected type found")
        }
    }

    private fun getEventOutputTopic(eventValue: Any): String {
        return when (eventValue) {
            is FlowEvent -> {
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
