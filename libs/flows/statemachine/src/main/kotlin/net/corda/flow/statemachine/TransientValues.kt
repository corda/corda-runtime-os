package net.corda.flow.statemachine

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.data.flow.event.FlowEvent
import net.corda.v5.application.services.serialization.SerializationService
import java.time.Clock
import java.util.concurrent.CompletableFuture

data class TransientValues(
    val checkpointSerializationService: SerializationService,
    val clock: Clock
) : KryoSerializable {
    val suspended: CompletableFuture<ByteArray?> = CompletableFuture()
    val eventsOut = mutableListOf<FlowEvent>()

    override fun write(kryo: Kryo?, output: Output?) {
        throw IllegalStateException("${TransientValues::class.qualifiedName} should never be serialized")
    }

    override fun read(kryo: Kryo?, input: Input?) {
        throw IllegalStateException("${TransientValues::class.qualifiedName} should never be deserialized")
    }
}