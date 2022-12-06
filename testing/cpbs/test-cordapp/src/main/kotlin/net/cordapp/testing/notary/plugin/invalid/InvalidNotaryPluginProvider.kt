package net.cordapp.testing.notary.plugin.invalid

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlowProvider
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryType
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@PluggableNotaryType("invalid-notary-plugin")
class InvalidNotaryPluginProvider : PluggableNotaryClientFlowProvider {
    override fun create(notary: Party, stx: UtxoSignedTransaction): PluggableNotaryClientFlow {
        throw IllegalStateException("Invalid plugin, cannot be loaded!")
    }
}
