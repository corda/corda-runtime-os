package net.corda.httprpc.durablestream

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class DurableStreamContext(val currentPosition: Long, val maxCount: Int)