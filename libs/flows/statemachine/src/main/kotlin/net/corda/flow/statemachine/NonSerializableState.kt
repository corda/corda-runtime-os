package net.corda.flow.statemachine

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.data.flow.event.FlowEvent
import net.corda.serialization.CheckpointSerializationService
import java.time.Clock
import java.util.concurrent.CompletableFuture

data class NonSerializableState(
    val checkpointSerializationService: CheckpointSerializationService,
    val clock: Clock
) : KryoSerializable {
    val suspended: CompletableFuture<ByteArray?> = CompletableFuture()
    val eventsOut = mutableListOf<FlowEvent>()

    override fun write(kryo: Kryo?, output: Output?) {
        throw IllegalStateException("${NonSerializableState::class.qualifiedName} should never be serialized")
    }

    override fun read(kryo: Kryo?, input: Input?) {
        throw IllegalStateException("${NonSerializableState::class.qualifiedName} should never be deserialized")
    }
}
