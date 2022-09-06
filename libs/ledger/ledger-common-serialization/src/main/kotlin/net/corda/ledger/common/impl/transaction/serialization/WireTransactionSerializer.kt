package net.corda.ledger.common.impl.transaction.serialization

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InternalCustomSerializer::class])
class WireTransactionSerializer @Activate constructor(
    @Reference(service = MerkleTreeFactory::class) private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = DigestService::class) private val digestService: DigestService
) : BaseProxySerializer<WireTransaction, WireTransactionSerializer.WireTransactionProxy>() {

    override fun toProxy(obj: WireTransaction): WireTransactionProxy =
        WireTransactionProxy(
            obj.privacySalt,
            obj.componentGroupLists
        )

    override fun fromProxy(proxy: WireTransactionProxy): WireTransaction =
        WireTransaction(
            merkleTreeFactory,
            digestService,
            proxy.privacySalt,
            proxy.componentGroupLists
        )

    override val proxyType: Class<WireTransactionProxy>
        get() = WireTransactionProxy::class.java
    override val type: Class<WireTransaction>
        get() = WireTransaction::class.java
    override val withInheritance: Boolean
        get() = true

    data class WireTransactionProxy(val privacySalt: PrivacySalt, val componentGroupLists: List<List<ByteArray>>)
}