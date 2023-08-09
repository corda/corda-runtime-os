package net.corda.ledger.utxo.flow.impl.groupparameters

import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.ledger.utxo.GroupParametersService
import net.corda.v5.membership.GroupParameters
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory

@Suppress("Unused")
@Component(
    service = [GroupParametersService::class, UsedByFlow::class],
    property = [SandboxConstants.CORDA_SYSTEM_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class GroupParametersServiceImpl  @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider
): GroupParametersService, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getCurrentGroupParameters(): GroupParameters {
        val groupReader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)
        val groupParameters = requireNotNull(groupReader.groupParameters) {
            "groupParameters could not be accessed."
        }
        return groupParameters
    }

    private val holdingIdentity: HoldingIdentity
        get() =
            currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity
}