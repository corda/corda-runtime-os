package com.r3.corda.notary.plugin.nonvalidating.client

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlowProvider
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryType
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * A provider class that will instantiate a [NonValidatingNotaryClientFlowImpl].
 * This class is used when installing the notary plugins on startup.
 */
@PluggableNotaryType("net.corda.notary.NonValidatingNotary")
class NonValidatingNotaryClientFlowProvider : PluggableNotaryClientFlowProvider {
    override fun create(notary: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow {
        return NonValidatingNotaryClientFlowImpl(stx, notary)
    }
}
