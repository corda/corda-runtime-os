package net.corda.ledger.utxo.data.transaction

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
enum class UtxoComponentGroup {
    METADATA,
    NOTARY,
    SIGNATORIES,
    OUTPUTS_INFO,
    COMMANDS_INFO,
    DATA_ATTACHMENTS,
    INPUTS,
    REFERENCES,
    OUTPUTS,
    COMMANDS,
}
