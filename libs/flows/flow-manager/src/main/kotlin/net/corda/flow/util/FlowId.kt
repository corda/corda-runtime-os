package net.corda.flow.util

import net.corda.v5.base.annotations.CordaSerializable
import java.util.*

/**
 * A unique identifier for a single top level flow instance, valid across node restarts. Note that a single run always
 * has at least one flow, but that flow may also invoke sub-flows: they all share the same flow id.
 */
@CordaSerializable
data class FlowId(val uuid: UUID) {
    companion object {
        fun createRandom(): FlowId = FlowId(UUID.randomUUID())
    }

    override fun toString(): String = "[$uuid]"
}