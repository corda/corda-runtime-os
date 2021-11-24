package net.corda.flow.statemachine

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.serialization.CheckpointSerializer

data class NonSerializableState(val checkpointSerializer: CheckpointSerializer) : KryoSerializable {

    override fun write(kryo: Kryo?, output: Output?) {
        throw IllegalStateException("${NonSerializableState::class.qualifiedName} should never be serialized")
    }

    override fun read(kryo: Kryo?, input: Input?) {
        throw IllegalStateException("${NonSerializableState::class.qualifiedName} should never be deserialized")
    }
}
