package net.corda.messaging.mediator

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.records.Record
fun <K: Any, E: Any> eventType(event: Record<K, E>): String {
    val eventValue = event.value
    val type = eventValue!!::class.java.simpleName
    return type + when (eventValue) {
        is FlowEvent -> ":" + eventType(eventValue)
        is FlowMapperEvent -> ":" + eventType(eventValue)
        else -> ""
    }
}

fun eventType(event: MediatorMessage<*>): String {
    val eventValue = event.payload
    val type = eventValue!!::class.java.simpleName
    return type + when (eventValue) {
        is FlowEvent -> ":" + eventType(eventValue)
        is FlowMapperEvent -> ":" + eventType(eventValue)
        else -> ""
    }
}

fun <K: Any, E: Any> eventType(event: CordaConsumerRecord<K, E>): String {
    val eventValue = event.value
    val type = eventValue!!::class.java.simpleName
    return type + when (eventValue) {
        is FlowEvent -> ":" + eventType(eventValue)
        is FlowMapperEvent -> ":" + eventType(eventValue)
        else -> ""
    }
}

private fun eventType(event: FlowEvent): String {
    val payload = event.payload
    val type = payload::class.java.simpleName
    val subType = if (payload is SessionEvent) {
        ":" + payload.payload::class.java.simpleName + "[${payload.sessionId}]"
    } else ""
    return type + subType
}

private fun eventType(event: FlowMapperEvent): String {
    val payload = event.payload
    val type = payload::class.java.simpleName
    val subType = if (payload is SessionEvent) {
        ":" + payload.payload::class.java.simpleName + "[${payload.sessionId}]"
    } else ""
    return type + subType
}
