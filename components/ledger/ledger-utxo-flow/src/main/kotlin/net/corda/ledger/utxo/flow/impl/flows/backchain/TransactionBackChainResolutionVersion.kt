package net.corda.ledger.utxo.flow.impl.flows.backchain

enum class TransactionBackChainResolutionVersion {
    V1, //  < 5.0.1
    V2  // >= 5.0.1
}