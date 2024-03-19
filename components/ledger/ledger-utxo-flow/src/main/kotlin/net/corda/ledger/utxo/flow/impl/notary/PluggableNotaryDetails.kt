package net.corda.ledger.utxo.flow.impl.notary

import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow

data class PluggableNotaryDetails(
    val flowClass: Class<out PluggableNotaryClientFlow>,
    val isBackchainRequired: Boolean
)
