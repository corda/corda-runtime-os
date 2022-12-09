package net.corda.ledger.notary.worker.selection

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.Party

/**
 * Performs a selection of notary workers for notary cluster. Used by notary client, which implements idempotent timed-flow logic:
 * if transaction cannot be notarised by current worker, the flow is restarted to send transaction to the next worker.
 * The selection is performed according to the implemented strategy, which is currently round-robin but can be more sophisticated.
 */
interface NotaryWorkerSelectorService {
    /**
     * Selects next notary worker from the provided [list].
     */
    @Suspendable
    fun next(list: List<Party>): Party
}
