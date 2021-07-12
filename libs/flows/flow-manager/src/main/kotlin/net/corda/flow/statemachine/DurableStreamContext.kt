package net.corda.flow.statemachine

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Duration

@CordaSerializable
data class DurableStreamContext(val currentPosition: Long, val maxCount: Int, val awaitForResultTimeout: Duration)