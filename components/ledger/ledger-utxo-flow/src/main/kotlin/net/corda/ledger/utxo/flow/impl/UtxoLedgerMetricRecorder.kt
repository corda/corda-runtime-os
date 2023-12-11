package net.corda.ledger.utxo.flow.impl

import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
/**
 * [UtxoLedgerMetricRecorder] records ledger metrics within flows.
 *
 * This is needed because [CurrentSandboxGroupContext] cannot be injected directly into flows.
 */
interface UtxoLedgerMetricRecorder {

    /**
     * Record a transaction backchain metric reading.
     *
     * @param length The resolved backchain length
     */
    fun recordTransactionBackchainLength(length: Int)
}
