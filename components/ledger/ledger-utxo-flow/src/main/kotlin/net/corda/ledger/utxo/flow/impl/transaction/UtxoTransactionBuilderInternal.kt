package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

interface UtxoTransactionBuilderInternal {
    val notary: Party?
    val timeWindow: TimeWindow?
    val attachments: List<SecureHash>
    val commands: List<Command>
    val signatories: List<PublicKey>
    val inputStateAndRefs: List<StateAndRef<*>>
    val referenceInputStateAndRefs: List<StateAndRef<*>>
    val outputStates: List<Pair<ContractState, Int?>>
}