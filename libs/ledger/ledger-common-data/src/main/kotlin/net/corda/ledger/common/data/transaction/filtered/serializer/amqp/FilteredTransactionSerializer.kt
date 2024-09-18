package net.corda.ledger.common.data.transaction.filtered.serializer.amqp

import net.corda.libs.json.validator.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.data.transaction.filtered.impl.FilteredTransactionImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class, UsedByVerification::class, UsedByPersistence::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class FilteredTransactionSerializer @Activate constructor(
    @Reference(service = JsonMarshallingService::class, scope = PROTOTYPE_REQUIRED)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class)
    private val jsonValidator: JsonValidator,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider
) : BaseProxySerializer<FilteredTransaction, FilteredTransactionProxy>(), UsedByFlow, UsedByVerification, UsedByPersistence {

    override val proxyType
        get() = FilteredTransactionProxy::class.java

    override val type
        get() = FilteredTransaction::class.java

    override val withInheritance
        // FilteredTransaction is an interface.
        get() = true

    override fun toProxy(obj: FilteredTransaction): FilteredTransactionProxy {
        return FilteredTransactionProxy(obj.id, obj.topLevelMerkleProof, obj.filteredComponentGroups, obj.privacySalt)
    }

    override fun fromProxy(proxy: FilteredTransactionProxy): FilteredTransaction {
        return FilteredTransactionImpl(
            proxy.id,
            proxy.topLevelMerkleProof,
            proxy.filteredComponentGroups,
            proxy.privacySalt,
            jsonMarshallingService,
            jsonValidator,
            merkleTreeProvider
        )
    }
}

class FilteredTransactionProxy(
    val id: SecureHash,
    val topLevelMerkleProof: MerkleProof,
    val filteredComponentGroups: Map<Int, FilteredComponentGroup>,
    val privacySalt: PrivacySalt
)
