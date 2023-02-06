package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class UtxoFilteredTransactionBuilderSerializer :
    BaseProxySerializer<UtxoFilteredTransactionBuilderInternal, UtxoFilteredTransactionBuilderProxy>(),
    UsedByFlow, UsedByVerification {

    override val type = UtxoFilteredTransactionBuilderInternal::class.java

    override val proxyType = UtxoFilteredTransactionBuilderProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: UtxoFilteredTransactionBuilderInternal): UtxoFilteredTransactionBuilderProxy {
        throw CordaRuntimeException("${UtxoFilteredTransactionBuilder::class.java.name} cannot be AMQP serialized and sent to peers")
    }

    override fun fromProxy(proxy: UtxoFilteredTransactionBuilderProxy): UtxoFilteredTransactionBuilderInternal {
        throw CordaRuntimeException("${UtxoFilteredTransactionBuilder::class.java.name} cannot be AMQP serialized and received from peers")
    }
}

/**
 * Unused, needed to satisfy the serializer's methods.
 */
class UtxoFilteredTransactionBuilderProxy