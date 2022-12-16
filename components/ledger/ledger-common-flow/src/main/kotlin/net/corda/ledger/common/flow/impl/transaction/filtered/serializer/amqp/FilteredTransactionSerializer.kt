package net.corda.ledger.common.flow.impl.transaction.filtered.serializer.amqp

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.flow.impl.transaction.filtered.FilteredTransactionImpl
import net.corda.ledger.common.flow.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class FilteredTransactionSerializer @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider
) : BaseProxySerializer<FilteredTransaction, FilteredTransactionProxy>(), UsedByFlow {

    override val proxyType = FilteredTransactionProxy::class.java

    override val type = FilteredTransaction::class.java

    override val withInheritance = true

    override fun toProxy(obj: FilteredTransaction): FilteredTransactionProxy {
        return FilteredTransactionProxy(obj.id, obj.topLevelMerkleProof, obj.filteredComponentGroups)
    }

    override fun fromProxy(proxy: FilteredTransactionProxy): FilteredTransaction {
        return FilteredTransactionImpl(
            proxy.id,
            proxy.topLevelMerkleProof,
            proxy.filteredComponentGroups,
            jsonMarshallingService,
            merkleTreeProvider
        )
    }
}

class FilteredTransactionProxy(
    val id: SecureHash,
    val topLevelMerkleProof: MerkleProof,
    val filteredComponentGroups: Map<Int, FilteredComponentGroup>
)