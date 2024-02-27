package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Component

@Suppress("unused")
@Component(service = [ InternalCustomSerializer::class ])
class EncumbranceGroupSerializer : BaseProxySerializer<EncumbranceGroupImpl, EncumbranceGroupProxy>() {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = EncumbranceGroupImpl::class.java

    override val proxyType: Class<EncumbranceGroupProxy>
        get() = EncumbranceGroupProxy::class.java

    override val withInheritance: Boolean
        // EncumbranceGroupImpl is a final class.
        get() = false

    override fun toProxy(obj: EncumbranceGroupImpl): EncumbranceGroupProxy {
        return EncumbranceGroupProxy(VERSION_1, obj.size, obj.tag)
    }

    override fun fromProxy(proxy: EncumbranceGroupProxy): EncumbranceGroupImpl {
        return when (proxy.version) {
            VERSION_1 ->
                EncumbranceGroupImpl(proxy.size, proxy.tag)
            else ->
                throw CordaRuntimeException("Unable to create EncumbranceGroup with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
class EncumbranceGroupProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for [EncumbranceGroupImpl] serialisation.
     */
    val size: Int,
    val tag: String
)
