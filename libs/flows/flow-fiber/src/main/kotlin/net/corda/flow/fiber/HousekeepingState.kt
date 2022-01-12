package net.corda.flow.fiber

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.requests.FlowIORequest
import java.util.concurrent.CompletableFuture

data class HousekeepingState(
    val checkpoint: Checkpoint,
    val input: FlowContinuation,
    val output: FlowIORequest<*>? = null,
    val suspended: CompletableFuture<ByteArray?> = CompletableFuture()
) : KryoSerializable {

    override fun write(kryo: Kryo?, output: Output?) {
        throw IllegalStateException("${HousekeepingState::class.qualifiedName} should never be serialized")
    }

    override fun read(kryo: Kryo?, input: Input?) {
        throw IllegalStateException("${HousekeepingState::class.qualifiedName} should never be deserialized")
    }
}
