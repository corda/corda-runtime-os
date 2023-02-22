package net.corda.ledger.notary.worker.selection.impl

import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

/**
 * Implements a random selection of notary virtual nodes.
 */
@Component(
    service = [ UsedByFlow::class, NotaryVirtualNodeSelectorService::class ],
    property = [SandboxConstants.CORDA_SYSTEM_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class NotaryVirtualNodeSelectorServiceImpl @Activate constructor(
    @Reference(service = MemberLookup::class)
    private val memberLookup: MemberLookup
): NotaryVirtualNodeSelectorService, SingletonSerializeAsToken, UsedByFlow {
    /**
     * This function will fetch the virtual nodes that belong to the [serviceIdentity] and do a random selection on
     * that list.
     */
    override fun selectVirtualNode(serviceIdentity: Party): Party {
        val selectedMember = memberLookup.lookup().filter {
            it.notaryDetails?.serviceName == serviceIdentity.name
        }.random()

        return Party(selectedMember.name, selectedMember.sessionInitiationKey)
    }
}
