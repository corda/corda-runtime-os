package net.corda.flow.application.services

import java.security.PublicKey
import net.corda.flow.fiber.FlowFiberService
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.MemberInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("Unused")
@Component(service = [MemberLookup::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class MemberLookupImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
) : MemberLookup, SingletonSerializeAsToken {

    @Suspendable
    override fun lookup(): List<MemberInfo> {
        return getGroupReader().lookup().toList()
    }

    @Suspendable
    override fun lookup(key: PublicKey): MemberInfo? {
        return getGroupReader().lookupByLedgerKey(key.calculateHash())
    }

    @Suspendable
    override fun lookup(name: MemberX500Name): MemberInfo? {
        return getGroupReader().lookup(name)
    }

    @Suspendable
    override fun myInfo(): MemberInfo {
        val name = flowFiberService.getExecutingFiber().getExecutionContext().memberX500Name
        return lookup(name)
            ?: throw IllegalStateException("Failed to find Member Info for the Virtual Node name='${name}'")
    }

    @Suspendable
    private fun getGroupReader(): MembershipGroupReader {
        return flowFiberService.getExecutingFiber().getExecutionContext().membershipGroupReader
    }
}