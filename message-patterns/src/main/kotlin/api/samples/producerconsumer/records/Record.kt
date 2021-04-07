package api.samples.producerconsumer.records

/**
   May/May not want to differentiate between state and event records for actor model.
   Kept record values as Strings for simplicity. Replace with generics/byte arrays
 */
open class Record (val eventSource: String, val key: String, var value:String)

class EventRecord(eventSource: String, key: String, value: String) : Record(eventSource, key, value)

class StateRecord(stateSource: String, key: String, value: String) : Record(stateSource, key, value)
