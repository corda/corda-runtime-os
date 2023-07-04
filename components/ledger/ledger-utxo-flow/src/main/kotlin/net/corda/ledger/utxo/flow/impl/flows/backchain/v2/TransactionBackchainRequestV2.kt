package net.corda.ledger.utxo.flow.impl.flows.backchain.v2

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
sealed class TransactionBackchainRequestV2 {
    data class Get(val transactionIds: Set<SecureHash>) : TransactionBackchainRequestV2()
    data class GetSignedGroupParameters(val groupParametersHash: SecureHash) : TransactionBackchainRequestV2()
    object Stop : TransactionBackchainRequestV2()
}
