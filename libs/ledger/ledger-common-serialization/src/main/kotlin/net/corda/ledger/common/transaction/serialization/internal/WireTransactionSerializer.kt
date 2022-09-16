package net.corda.ledger.common.transaction.serialization.internal

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.transaction.serialization.WireTransactionContainer
import net.corda.ledger.common.transaction.serialization.WireTransactionVersion
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InternalCustomSerializer::class])
class WireTransactionSerializer @Activate constructor(
    @Reference(service = MerkleTreeFactory::class) private val merkleTreeFactory: MerkleTreeFactory,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class) private val jsonMarshallingService: JsonMarshallingService
) : BaseProxySerializer<WireTransaction, WireTransactionContainer>() {

    override fun toProxy(obj: WireTransaction): WireTransactionContainer =
        WireTransactionContainer(
            WireTransactionVersion.VERSION_1,
            obj.privacySalt,
            obj.componentGroupLists
        )

    override fun fromProxy(proxy: WireTransactionContainer): WireTransaction {
        if (proxy.version == WireTransactionVersion.VERSION_1) {
            return WireTransaction(
                merkleTreeFactory,
                digestService,
                jsonMarshallingService,
                proxy.privacySalt,
                proxy.componentGroupLists
            )
        }
        throw CordaRuntimeException("Unable to create WireTransaction with Version='${proxy.version}'")
    }

    override val proxyType: Class<WireTransactionContainer>
        get() = WireTransactionContainer::class.java
    override val type: Class<WireTransaction>
        get() = WireTransaction::class.java
    override val withInheritance: Boolean
        get() = true
}