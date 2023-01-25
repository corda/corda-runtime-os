package net.corda.httprpc.durablestream

data class DurableStreamContext(val currentPosition: Long, val maxCount: Int)