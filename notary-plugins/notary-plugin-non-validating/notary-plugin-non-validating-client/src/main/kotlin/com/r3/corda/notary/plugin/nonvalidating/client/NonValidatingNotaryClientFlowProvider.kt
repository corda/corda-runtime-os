package com.r3.corda.notary.plugin.nonvalidating.client

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlowProvider
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryType
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * A provider class that will instantiates a [NonValidatingNotaryClientFlowImpl].
 * This class is used when installing the notary plugins on startup.
 */
@PluggableNotaryType("corda.notary.type.non-validating")
class NonValidatingNotaryClientFlowProvider : PluggableNotaryClientFlowProvider {
    override fun create(notary: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow {
        return NonValidatingNotaryClientFlowImpl(stx, notary)
    }
}