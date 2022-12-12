package net.corda.ledger.notary.worker.selection.impl

import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.sandbox.type.UsedByFlow
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
    service = [ UsedByFlow::class ],
    scope = ServiceScope.PROTOTYPE
)
class NotaryVirtualNodeSelectorServiceImpl @Activate constructor(
    @Reference(service = NotaryVirtualNodeLookup::class)
    private val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
): NotaryVirtualNodeSelectorService, SingletonSerializeAsToken, UsedByFlow {

    /**
     * This function will fetch the virtual nodes that belong to the [serviceIdentity] and do a random selection on
     * that list.
     */
    override fun next(serviceIdentity: Party): Party {
        val workers = notaryVirtualNodeLookup.getNotaryVirtualNodes(serviceIdentity.name)
        val selectedMember = workers.random()

        return Party(selectedMember.name, selectedMember.sessionInitiationKey)
    }
}
