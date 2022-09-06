package net.corda.ledger.common.transaction.serialization.internal

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.transaction.serialization.WireTransactionContainerImpl
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InternalCustomSerializer::class])
class WireTransactionSerializer @Activate constructor(
    @Reference(service = MerkleTreeFactory::class) private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = DigestService::class) private val digestService: DigestService
) : BaseProxySerializer<WireTransaction, WireTransactionContainerImpl>() {

    override fun toProxy(obj: WireTransaction): WireTransactionContainerImpl =
        WireTransactionContainerImpl(
            WireTransactionVersion.VERSION_1,
            obj.privacySalt,
            obj.componentGroupLists
        )

    override fun fromProxy(proxy: WireTransactionContainerImpl): WireTransaction {
        //TODO(check metadata)

        return WireTransaction(
            merkleTreeFactory,
            digestService,
            proxy.privacySalt,
            proxy.componentGroupLists
        )
    }

    override val proxyType: Class<WireTransactionContainerImpl>
        get() = WireTransactionContainerImpl::class.java
    override val type: Class<WireTransaction>
        get() = WireTransaction::class.java
    override val withInheritance: Boolean
        get() = true
}