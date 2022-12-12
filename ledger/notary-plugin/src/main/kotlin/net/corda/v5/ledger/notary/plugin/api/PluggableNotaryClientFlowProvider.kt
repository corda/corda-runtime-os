package net.corda.v5.ledger.notary.plugin.api

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * This interface is used to instantiate the client-side logic (flow) of the plugin.
 *
 * Implementers of this interface must have the [PluggableNotaryType] annotation,
 * otherwise an exception will be thrown on loading.
 *
 * The client-side of the plugin must have the same constructor parameters as the
 * [create] function (notary, stx).
 */
interface PluggableNotaryClientFlowProvider {
    fun create(notary: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow
}
