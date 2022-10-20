package net.corda.ledger.common.data.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InternalCustomSerializer::class])
class WireTransactionSerializer @Activate constructor(
    @Reference(service = MerkleTreeProvider::class) private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class) private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class) private val jsonMarshallingService: JsonMarshallingService
) : BaseProxySerializer<WireTransaction, WireTransactionProxy>() {

    override val type = WireTransaction::class.java

    override val proxyType = WireTransactionProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: WireTransaction): WireTransactionProxy {
        return WireTransactionProxy(
            WireTransactionVersion.VERSION_1,
            obj.privacySalt,
            obj.componentGroupLists
        )
    }

    override fun fromProxy(proxy: WireTransactionProxy): WireTransaction {
        if (proxy.version == WireTransactionVersion.VERSION_1) {
            return WireTransaction(
                merkleTreeProvider,
                digestService,
                jsonMarshallingService,
                proxy.privacySalt,
                proxy.componentGroupLists
            )
        }
        throw CordaRuntimeException("Unable to create WireTransaction with Version='${proxy.version}'")
    }
}

/**
 * The class that actually gets serialized on the wire.
 */

data class WireTransactionProxy(
    /**
     * Version of container.
     */
    val version: WireTransactionVersion,

    /**
     * Properties for wire transactions' serialisation.
     */
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>
)

/**
 * Enumeration for WireTransaction version.
 */
@CordaSerializable
enum class WireTransactionVersion {
    VERSION_1
}