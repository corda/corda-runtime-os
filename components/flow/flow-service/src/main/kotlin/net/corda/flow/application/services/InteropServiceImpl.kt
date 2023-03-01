package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberService
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.InteropService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [ InteropService::class, UsedByFlow::class ], scope = ServiceScope.PROTOTYPE)
class InteropServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : InteropService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun callFacade(memberName: MemberX500Name, facadeName: String, methodName: String, payload: String): String {
        TODO("Not yet implemented")
    }
}
