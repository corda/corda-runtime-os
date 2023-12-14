package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.TransactionState
import java.security.PublicKey

@CordaSerializable
data class ContractStateAndEncumbranceTag(val contractState: ContractState, val encumbranceTag: String?) {

    fun toTransactionState(notaryName: MemberX500Name, notaryKey: PublicKey, encumbranceGroupSize: Int?): TransactionState<*> {
        return TransactionStateImpl(
            contractState,
            notaryName,
            notaryKey,
            encumbranceTag?.let {
                requireNotNull(encumbranceGroupSize)
                EncumbranceGroupImpl(encumbranceGroupSize, it)
            }
        )
    }
}
