package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.EncumbranceGroup
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class, UsedByVerification::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class TransactionStateSerializer @Activate constructor() :
    BaseProxySerializer<TransactionStateImpl<ContractState>, TransactionStateProxy>(), UsedByFlow, UsedByVerification {

    override val type = TransactionStateImpl::class.java

    override val proxyType = TransactionStateProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: TransactionStateImpl<ContractState>): TransactionStateProxy {
        return TransactionStateProxy(
            TransactionStateVersion.VERSION_1,
            obj.contractState,
            obj.notary,
            obj.encumbranceGroup
        )
    }

    override fun fromProxy(proxy: TransactionStateProxy): TransactionStateImpl<ContractState> {
        if (proxy.version == TransactionStateVersion.VERSION_1) {
            return TransactionStateImpl(
                proxy.contractState,
                proxy.notary,
                proxy.encumbrance
            )
        }
        throw CordaRuntimeException("Unable to create TransactionStateImpl with Version='${proxy.version}'")
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class TransactionStateProxy(
    /**
     * Version of container.
     */
    val version: TransactionStateVersion,

    /**
     * Properties for transaction state serialisation.
     */
    val contractState: ContractState,
    val notary: Party,
    val encumbrance: EncumbranceGroup?,
)

/**
 * Enumeration for transaction state version.
 */
@CordaSerializable
enum class TransactionStateVersion {
    VERSION_1
}