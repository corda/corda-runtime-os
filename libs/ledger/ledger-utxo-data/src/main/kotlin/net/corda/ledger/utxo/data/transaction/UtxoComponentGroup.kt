package net.corda.ledger.utxo.data.transaction

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

@CordaSerializable
enum class UtxoComponentGroup {
    METADATA,
    NOTARY,
    SIGNATORIES,
    OUTPUTS_INFO,
    COMMANDS_INFO,
    UNUSED, // can't remove to keep the Merkle hash stable
    INPUTS,
    REFERENCES,
    OUTPUTS,
    COMMANDS,
}

val utxoComponentGroupStructure = listOf(
    listOf("metadata"),
    listOf(MemberX500Name::class.java.name, PublicKey::class.java.name, TimeWindow::class.java.name),
    listOf(PublicKey::class.java.name),
    listOf(UtxoOutputInfoComponent::class.java.name),
    listOf("CommandInfo"),
    listOf(SecureHash::class.java.name),
    listOf(StateRef::class.java.name),
    listOf(StateRef::class.java.name),
    listOf(ContractState::class.java.name),
    listOf(Command::class.java.name),
)
