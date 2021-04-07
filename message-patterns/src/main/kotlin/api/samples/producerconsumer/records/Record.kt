package api.samples.producerconsumer.records

/**
   May/May not want to differentiate between state and event records for actor model.
   Kept record values as Strings for simplicity.
 */
open class Record<T>(val eventSource: String, val key: String, var value: T)

class EventRecord<T>(eventSource: String, key: String, value: T) : Record<T>(eventSource, key, value)

class StateRecord<T>(stateSource: String, key: String, value: T) : Record<T>(stateSource, key, value)
