package net.corda.ledger.common.data.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class WireTransactionSerializer @Activate constructor(
    @Reference(service = WireTransactionFactory::class)
    private val wireTransactionFactory: WireTransactionFactory
) : BaseProxySerializer<WireTransaction, WireTransactionProxy>(), UsedByFlow, UsedByPersistence, UsedByVerification {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = WireTransaction::class.java

    override val proxyType
        get() = WireTransactionProxy::class.java

    override val withInheritance
        // WireTransaction is a final class.
        get() = false

    override fun toProxy(obj: WireTransaction): WireTransactionProxy {
        return WireTransactionProxy(
            VERSION_1,
            obj.privacySalt,
            obj.componentGroupLists
        )
    }

    override fun fromProxy(proxy: WireTransactionProxy): WireTransaction {
        return when (proxy.version) {
            VERSION_1 ->
                wireTransactionFactory.create(
                    proxy.componentGroupLists,
                    proxy.privacySalt
                )
            else ->
                throw CordaRuntimeException("Unable to create WireTransaction with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class WireTransactionProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for wire transactions' serialisation.
     */
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>
)
