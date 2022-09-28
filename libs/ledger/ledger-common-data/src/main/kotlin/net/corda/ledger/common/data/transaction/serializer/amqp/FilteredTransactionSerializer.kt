package net.corda.ledger.common.data.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.filtered.FilteredTransactionImpl
import net.corda.ledger.common.data.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InternalCustomSerializer::class])
class FilteredTransactionSerializer @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider
) : BaseProxySerializer<FilteredTransaction, FilteredTransactionProxy>() {

    override val proxyType = FilteredTransactionProxy::class.java

    override val type = FilteredTransaction::class.java

    override val withInheritance = true

    override fun toProxy(obj: FilteredTransaction): FilteredTransactionProxy {
        return FilteredTransactionProxy(obj.id, obj.componentGroupMerkleProof, obj.filteredComponentGroups)
    }

    override fun fromProxy(proxy: FilteredTransactionProxy): FilteredTransaction {
        return FilteredTransactionImpl(
            proxy.id,
            proxy.componentGroupProof,
            proxy.filteredComponentGroups,
            jsonMarshallingService,
            merkleTreeProvider
        )
    }
}

class FilteredTransactionProxy(
    val id: SecureHash,
    val componentGroupProof: MerkleProof,
    val filteredComponentGroups: Map<Int, FilteredComponentGroup>
)