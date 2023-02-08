package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class UtxoTransactionBuilderSerializer :
    BaseProxySerializer<UtxoTransactionBuilderInternal, UtxoTransactionBuilderProxy>(),
    UsedByFlow, UsedByVerification {

    override val type = UtxoTransactionBuilderInternal::class.java

    override val proxyType = UtxoTransactionBuilderProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: UtxoTransactionBuilderInternal): UtxoTransactionBuilderProxy {
        throw CordaRuntimeException("${UtxoTransactionBuilder::class.java.name} cannot be AMQP serialized and sent to peers")
    }

    override fun fromProxy(proxy: UtxoTransactionBuilderProxy): UtxoTransactionBuilderInternal {
        throw CordaRuntimeException("${UtxoTransactionBuilder::class.java.name} cannot be AMQP serialized and received from peers")
    }
}

/**
 * Unused, needed to satisfy the serializer's methods.
 */
class UtxoTransactionBuilderProxy