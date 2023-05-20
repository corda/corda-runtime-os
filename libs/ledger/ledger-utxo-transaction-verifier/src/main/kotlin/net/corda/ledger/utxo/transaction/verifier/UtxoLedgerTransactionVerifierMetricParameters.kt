package net.corda.ledger.utxo.transaction.verifier

import net.corda.virtualnode.HoldingIdentity

data class UtxoLedgerTransactionVerifierMetricParameters(val holdingIdentity: HoldingIdentity, val flowId: String)