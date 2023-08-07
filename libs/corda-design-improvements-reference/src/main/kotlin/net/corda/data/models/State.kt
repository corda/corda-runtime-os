package net.corda.data.models

data class State<V: Any>(val key: String, val payload: V, val type: String?, val tags: Map<String, String>?, val version: Int)
