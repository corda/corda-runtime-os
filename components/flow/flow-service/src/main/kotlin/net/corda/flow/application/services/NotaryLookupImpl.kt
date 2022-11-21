package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberService
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.membership.NotaryInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [ NotaryLookup::class, UsedByFlow::class ], scope = PROTOTYPE)
class NotaryLookupImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
) : NotaryLookup, UsedByFlow, SingletonSerializeAsToken {
    @Suspendable
    override val notaryServices: Collection<NotaryInfo>
        get() = notaries ?: emptyList()

    @Suspendable
    override fun isNotaryVirtualNode(virtualNodeName: MemberX500Name): Boolean =
        groupReader.lookup(virtualNodeName)?.notaryDetails?.let {
            lookup(it.serviceName)
        } != null

    @Suspendable
    override fun lookup(notaryServiceName: MemberX500Name): NotaryInfo? {
        return notaryServices.firstOrNull {
            it.name == notaryServiceName
        }
    }

    @Suspendable
    private val groupReader
        get() = flowFiberService.getExecutingFiber().getExecutionContext().membershipGroupReader

    @Suspendable
    private val notaries
        get() = groupReader.groupParameters?.notaries
}
