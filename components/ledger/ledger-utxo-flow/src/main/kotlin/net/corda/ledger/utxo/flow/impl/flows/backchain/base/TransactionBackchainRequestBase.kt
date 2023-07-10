package net.corda.ledger.utxo.flow.impl.flows.backchain.base

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
sealed class TransactionBackchainRequestBase {
    data class Get(val transactionIds: Set<SecureHash>) : TransactionBackchainRequestBase()
    data class GetSignedGroupParameters(val groupParametersHash: SecureHash) : TransactionBackchainRequestBase()
    object Stop : TransactionBackchainRequestBase()
}
