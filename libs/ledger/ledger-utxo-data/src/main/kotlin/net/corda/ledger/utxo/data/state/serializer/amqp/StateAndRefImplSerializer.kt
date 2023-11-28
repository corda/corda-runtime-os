package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import org.osgi.service.component.annotations.Component

@Component(service = [ InternalCustomSerializer::class ])
class StateAndRefSerializer : BaseProxySerializer<StateAndRefImpl<ContractState>, StateAndRefProxy>() {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = StateAndRefImpl::class.java

    override val proxyType
        get() = StateAndRefProxy::class.java

    override val withInheritance
        // StateAndRefImpl is a final class.
        get() = false

    override fun toProxy(obj: StateAndRefImpl<ContractState>): StateAndRefProxy {
        return StateAndRefProxy(
            VERSION_1,
            obj.state,
            obj.ref
        )
    }

    override fun fromProxy(proxy: StateAndRefProxy): StateAndRefImpl<ContractState> {
        return when (proxy.version) {
            VERSION_1 ->
                StateAndRefImpl(
                    proxy.state,
                    proxy.ref
                )
            else ->
                throw CordaRuntimeException("Unable to create StateAndRefImpl with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class StateAndRefProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for [StateAndRefImpl] serialisation.
     */
    val state: TransactionState<ContractState>,
    val ref: StateRef
)
