package net.corda.messaging.api.records

class Record<K, V>(val topic: String, val key: K, val value: V)
