package net.cordax.flowworker.api.records

class StateAndEvent<K, S, E>(val state: Record<K, S>?, val event:  Record<K, E>)
