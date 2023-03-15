package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
sealed interface TransactionBackchainRequestV1 {
    data class Get(val transactionIds: Set<SecureHash>): TransactionBackchainRequestV1
    object Stop: TransactionBackchainRequestV1
}