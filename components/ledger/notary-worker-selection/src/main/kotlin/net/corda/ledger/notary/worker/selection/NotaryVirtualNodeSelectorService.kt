package net.corda.ledger.notary.worker.selection

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.Party

/**
 * A service that is used to select the next notary virtual node for the given notary service.
 */
interface NotaryVirtualNodeSelectorService {
    /**
     * Selects next notary virtual node from the virtual nodes that belong to the [serviceIdentity].
     */
    @Suspendable
    fun next(serviceIdentity: Party): Party
}
