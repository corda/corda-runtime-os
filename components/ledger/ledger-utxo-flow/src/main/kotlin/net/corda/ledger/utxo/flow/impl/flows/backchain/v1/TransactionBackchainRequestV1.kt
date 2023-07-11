package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

/**
 * This class is transferred both in V1 and V2 of Transaction Backchain resolution flows.
 * So its name/package should not be changed (without flow version upgrade).
 */

@CordaSerializable
sealed class TransactionBackchainRequestV1 {
    data class Get(val transactionIds: Set<SecureHash>) : TransactionBackchainRequestV1()
    data class GetSignedGroupParameters(val groupParametersHash: SecureHash) : TransactionBackchainRequestV1()
    object Stop : TransactionBackchainRequestV1()
}
