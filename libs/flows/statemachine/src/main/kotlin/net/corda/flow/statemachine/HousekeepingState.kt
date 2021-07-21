package net.corda.flow.statemachine

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.data.flow.event.FlowEvent
import net.corda.v5.application.identity.Party

data class HousekeepingState(
    val suspendCount: Int,
    val ourIdentity: Party,
    val isKilled: Boolean,
//        val sessions: MutableMap<Trace.SessionId, FlowSessionImpl>,
//        val subFlows: MutableList<SubFlow>,
    val eventsIn: MutableList<FlowEvent>
) : KryoSerializable {
    override fun write(kryo: Kryo?, output: Output?) {
        throw IllegalStateException("${HousekeepingState::class.qualifiedName} should never be serialized")
    }

    override fun read(kryo: Kryo?, input: Input?) {
        throw IllegalStateException("${HousekeepingState::class.qualifiedName} should never be deserialized")
    }
}
