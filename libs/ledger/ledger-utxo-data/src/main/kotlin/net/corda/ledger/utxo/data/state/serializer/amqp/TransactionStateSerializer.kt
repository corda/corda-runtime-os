package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import org.osgi.service.component.annotations.Component
import java.security.PublicKey

@Component(service = [ InternalCustomSerializer::class ])
class TransactionStateSerializer : BaseProxySerializer<TransactionStateImpl<ContractState>, TransactionStateProxy>() {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = TransactionStateImpl::class.java

    override val proxyType
        get() = TransactionStateProxy::class.java

    override val withInheritance
        // TransactionStateImpl is a final class.
        get() = false

    override fun toProxy(obj: TransactionStateImpl<ContractState>): TransactionStateProxy {
        return TransactionStateProxy(
            VERSION_1,
            obj.contractState,
            obj.notaryName,
            obj.notaryKey,
            obj.encumbranceGroup
        )
    }

    override fun fromProxy(proxy: TransactionStateProxy): TransactionStateImpl<ContractState> {
        return when (proxy.version) {
            VERSION_1 ->
                TransactionStateImpl(
                    proxy.contractState,
                    proxy.notaryName,
                    proxy.notaryKey,
                    proxy.encumbrance
                )
            else ->
                throw CordaRuntimeException("Unable to create TransactionStateImpl with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class TransactionStateProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for transaction state serialisation.
     */
    val contractState: ContractState,
    val notaryName: MemberX500Name,
    val notaryKey: PublicKey,
    val encumbrance: EncumbranceGroup?,
)
