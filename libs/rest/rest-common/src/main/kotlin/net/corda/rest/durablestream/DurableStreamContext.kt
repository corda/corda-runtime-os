package net.corda.rest.durablestream

data class DurableStreamContext(val currentPosition: Long, val maxCount: Int)