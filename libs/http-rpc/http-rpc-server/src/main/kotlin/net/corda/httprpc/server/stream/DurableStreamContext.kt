package net.corda.internal.base.stream

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class DurableStreamContext(val currentPosition: Long, val maxCount: Int)