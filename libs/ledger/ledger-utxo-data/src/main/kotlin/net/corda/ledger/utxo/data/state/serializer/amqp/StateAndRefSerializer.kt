package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class, UsedByVerification::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class StateAndRefSerializer @Activate constructor()
    : BaseProxySerializer<StateAndRefImpl<ContractState>, StateAndRefProxy>(), UsedByFlow, UsedByVerification {

    override val type = StateAndRefImpl::class.java

    override val proxyType = StateAndRefProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: StateAndRefImpl<ContractState>): StateAndRefProxy {
        return StateAndRefProxy(
            StateAndRefVersion.VERSION_1,
            obj.state,
            obj.ref
        )
    }

    override fun fromProxy(proxy: StateAndRefProxy): StateAndRefImpl<ContractState> {
        if (proxy.version == StateAndRefVersion.VERSION_1) {
            return StateAndRefImpl(
                proxy.state,
                proxy.ref
            )
        }
        throw CordaRuntimeException("Unable to create StateAndRefImpl with Version='${proxy.version}'")
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class StateAndRefProxy(
    /**
     * Version of container.
     */
    val version: StateAndRefVersion,

    /**
     * Properties for StateAndRef serialisation.
     */
    val state: TransactionState<ContractState>,
    val ref: StateRef
)

/**
 * Enumeration for StateAndRef version.
 */
@CordaSerializable
enum class StateAndRefVersion {
    VERSION_1
}