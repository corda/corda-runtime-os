package net.corda.membership.lib.impl.serializer.amqp

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.impl.MGMContextImpl
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    property = [SandboxConstants.CORDA_UNINJECTABLE_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class MGMContextSerializer @Activate constructor(
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : BaseProxySerializer<MGMContextImpl, MGMContextProxy>(), UsedByFlow, UsedByPersistence, UsedByVerification {
    private companion object {
        private const val VERSION_1 = 1
    }

    override fun toProxy(obj: MGMContextImpl): MGMContextProxy {
        return MGMContextProxy(
            VERSION_1,
            obj.toMap()
        )
    }

    override fun fromProxy(proxy: MGMContextProxy): MGMContextImpl {
        return when(proxy.version) {
            VERSION_1 ->
                MGMContextImpl(layeredPropertyMapFactory.createMap(proxy.map))
            else ->
                throw CordaRuntimeException("Unable to create MGMContextImpl with Version='${proxy.version}'")
        }
    }

    override val proxyType: Class<MGMContextProxy>
        get() = MGMContextProxy::class.java

    override val type: Class<MGMContextImpl>
        get() = MGMContextImpl::class.java

    override val withInheritance: Boolean
        get() = false

    private fun LayeredPropertyMap.toMap() = this.entries.associate { it.key to it.value }

}

/**
 * The class that actually gets serialized on the wire.
 */
data class MGMContextProxy(
    /**
     * Version of container.
     */
    val version: Int,
    /**
     * Properties for [MGMContextImpl] serialization.
     */
    val map: Map<String, String?>
)
