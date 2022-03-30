package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberService
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.services.MemberLookupService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Suppress("Unused")
@Component(service = [MemberLookupService::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class MemberLookupServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
) : MemberLookupService, SingletonSerializeAsToken, CordaFlowInjectable {
    override fun lookup(): List<MemberInfo> {
        return getGroupReader().lookup().toList()
    }

    override fun lookup(key: PublicKey): MemberInfo? {
        return getGroupReader().lookup(key.calculateHash())
    }

    override fun lookup(name: MemberX500Name): MemberInfo? {
        return getGroupReader().lookup(name)
    }

    override fun myInfo(): MemberInfo {
        val name = flowFiberService.getExecutingFiber().getExecutionContext().memberX500Name
        return lookup(name)
            ?: throw IllegalStateException("Failed to find Member Info for the Virtual Node name='${name}'")
    }

    private fun getGroupReader(): MembershipGroupReader {
        return flowFiberService.getExecutingFiber().getExecutionContext().membershipGroupReader
    }
}