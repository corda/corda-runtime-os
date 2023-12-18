package net.corda.ledger.utxo.flow.impl.state.serializer.kryo

import net.corda.ledger.utxo.data.state.LazyStateAndRefImpl
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.StateAndRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import kotlin.reflect.jvm.isAccessible

@Component(
    service = [CheckpointInternalCustomSerializer::class, UsedByFlow::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class LazyStateAndRefImplKryoSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serialisationService: SerializationService,
) : CheckpointInternalCustomSerializer<LazyStateAndRefImpl<*>>, UsedByFlow {

    private companion object {
        const val NOT_INITIALIZED = 0
        const val INITIALIZED = 1
    }

    override val type: Class<LazyStateAndRefImpl<*>> get() = LazyStateAndRefImpl::class.java

    override fun write(output: CheckpointOutput, obj: LazyStateAndRefImpl<*>) {
        output.writeClassAndObject(obj.serializedStateAndRef)
        val delegate = obj::stateAndRef
            .also { property -> property.isAccessible = true }
            .getDelegate()
        @Suppress("UNCHECKED_CAST")
        if ((delegate as Lazy<StateAndRef<*>>).isInitialized()) {
            output.writeInt(INITIALIZED)
            output.writeClassAndObject(obj.stateAndRef)
        } else {
            output.writeInt(NOT_INITIALIZED)
        }
    }

    override fun read(input: CheckpointInput, type: Class<out LazyStateAndRefImpl<*>>): LazyStateAndRefImpl<*> {
        val serializedStateAndRef = input.readClassAndObject() as UtxoVisibleTransactionOutputDto
        val deserializedStateAndRef = when (input.readInt()) {
            0 -> null
            1 -> input.readClassAndObject() as StateAndRef<*>
            else -> throw IllegalArgumentException(
                "${LazyStateAndRefImpl::class.java} was previously serialized incorrectly and cannot be deserialized"
            )
        }
        return LazyStateAndRefImpl(serializedStateAndRef, deserializedStateAndRef, serialisationService)
    }
}
