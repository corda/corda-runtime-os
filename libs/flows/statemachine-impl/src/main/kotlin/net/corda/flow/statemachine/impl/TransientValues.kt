package net.corda.flow.statemachine.impl

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.data.flow.event.FlowEvent
import net.corda.v5.application.services.serialization.SerializationService
import java.time.Clock

data class TransientValues(
    val checkpointSerializationService: SerializationService,
    val clock: Clock
) : KryoSerializable {
    var suspended: ByteArray? = null
    val eventsOut = mutableListOf<FlowEvent>()

    override fun write(kryo: Kryo?, output: Output?) {
        throw IllegalStateException("${TransientValues::class.qualifiedName} should never be serialized")
    }

    override fun read(kryo: Kryo?, input: Input?) {
        throw IllegalStateException("${TransientValues::class.qualifiedName} should never be deserialized")
    }
}