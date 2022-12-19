package net.corda.ledger.notary.plugin.factory

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * This interface provides a service that will be used in notary selection logic to select the proper notary plugin.
 */
interface PluggableNotaryClientFlowFactory {
    @Suspendable
    fun create(notaryService: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow
}
