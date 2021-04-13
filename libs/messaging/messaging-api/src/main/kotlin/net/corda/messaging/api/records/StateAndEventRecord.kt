package net.corda.messaging.api.records

/**
 * Object to encapsulate the events stored on topics which also have a state
 * [state] the state record for a given event. Can be null if event has no state.
 * [event] the event record.
 * [state] and [event] must have the same key type but their values can be of different types.
 */
class StateAndEventRecord<K, S, E>(val state: Record<K, S>?, val event: Record<K, E>)
