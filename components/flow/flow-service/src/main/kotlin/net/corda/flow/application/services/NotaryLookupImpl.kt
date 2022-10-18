package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberService
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.v5.application.membership.NotaryLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Component(
    service = [NotaryLookup::class, SingletonSerializeAsToken::class],
    scope = ServiceScope.PROTOTYPE,
    property = ["corda.system=true"]
)
class NotaryLookupImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
) : NotaryLookup, SingletonSerializeAsToken {
    @Suppress("ForbiddenComment")
    // TODO: Change implementation to use the `groupReader.groupParameters`

    @Suspendable
    override val notaryServices: List<NotaryInfo>
        get() = members.map {
            it.notaryDetails
        }
            .filterNotNull()
            .groupBy {
                it.serviceName
            }.mapNotNull {
                val plugin = it.value.firstNotNullOfOrNull { notaryDetails ->
                    notaryDetails.servicePlugin
                }
                if (plugin == null) {
                    null
                } else {
                    NotaryInfoImpl(
                        party = it.key,
                        pluginClass = plugin
                    )
                }
            }

    @Suspendable
    override fun isNotaryVirtualNode(virtualNodeName: MemberX500Name): Boolean =
        groupReader.lookup(virtualNodeName)?.notaryDetails?.let {
            lookup(it.serviceName)
        } != null

    @Suspendable
    override fun lookup(publicKey: PublicKey): NotaryInfo? {
        val serviceName = members.map {
            it.notaryDetails
        }.filterNotNull()
            .filter {
                it.keys.any { key ->
                    key.publicKey == publicKey
                }
            }.map {
                it.serviceName
            }.firstOrNull() ?: return null
        return lookup(serviceName)
    }

    @Suspendable
    override fun lookup(notaryServiceName: MemberX500Name): NotaryInfo? {
        val plugin = members.map {
            it.notaryDetails
        }.filterNotNull()
            .filter { it.serviceName == notaryServiceName }
            .firstNotNullOfOrNull { it.servicePlugin }
            ?: return null
        return NotaryInfoImpl(
            notaryServiceName,
            plugin
        )
    }

    @Suspendable
    private val groupReader
        get() = flowFiberService.getExecutingFiber().getExecutionContext().membershipGroupReader

    @Suspendable
    private val members
        get() = groupReader.lookup().asSequence()

    private data class NotaryInfoImpl(
        override val party: MemberX500Name,
        override val pluginClass: String
    ) : NotaryInfo
}
