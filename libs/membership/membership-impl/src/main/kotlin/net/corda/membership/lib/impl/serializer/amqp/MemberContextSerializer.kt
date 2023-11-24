package net.corda.membership.lib.impl.serializer.amqp

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.impl.MemberContextImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
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
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = ServiceScope.PROTOTYPE
)
class MemberContextSerializer @Activate constructor(
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : BaseProxySerializer<MemberContextImpl, MemberContextProxy>(), UsedByFlow, UsedByPersistence, UsedByVerification {
    private companion object {
        private const val VERSION_1 = 1
    }

    override fun toProxy(obj: MemberContextImpl): MemberContextProxy {
        return MemberContextProxy(
            VERSION_1,
            obj.toMap()
        )
    }

    override fun fromProxy(proxy: MemberContextProxy): MemberContextImpl {
        return when(proxy.version) {
            VERSION_1 ->
                MemberContextImpl(layeredPropertyMapFactory.createMap(proxy.map))
            else ->
                throw CordaRuntimeException("Unable to create MemberContextImpl with Version='${proxy.version}'")
        }
    }

    override val proxyType: Class<MemberContextProxy>
        get() = MemberContextProxy::class.java

    override val type: Class<MemberContextImpl>
        get() = MemberContextImpl::class.java

    override val withInheritance: Boolean
        get() = false

    private fun LayeredPropertyMap.toMap() = this.entries.associate { it.key to it.value }

}

/**
 * The class that actually gets serialized on the wire.
 */
data class MemberContextProxy(
    /**
     * Version of container.
     */
    val version: Int,
    /**
     * Properties for [MemberContextImpl] serialization.
     */
    val map: Map<String, String?>
)
