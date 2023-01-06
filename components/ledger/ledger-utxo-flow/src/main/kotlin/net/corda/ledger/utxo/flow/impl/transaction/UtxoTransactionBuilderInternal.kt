package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

interface UtxoTransactionBuilderInternal {
    val notary: Party?
    val timeWindow: TimeWindow?
    val attachments: List<SecureHash>
    val commands: List<Command>
    val signatories: List<PublicKey>
    val inputStateRefs: List<StateRef>
    val referenceStateRefs: List<StateRef>
    val outputStates: List<ContractStateAndEncumbranceTag>
}