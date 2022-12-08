package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
sealed interface TransactionBackchainRequest {
    data class Get(val transactionIds: Set<SecureHash>): TransactionBackchainRequest
    object Stop: TransactionBackchainRequest
}