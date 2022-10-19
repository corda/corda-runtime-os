package net.corda.ledger.utxo.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
enum class UtxoComponentGroup {
    METADATA,
    NOTARY,
    OUTPUTS_INFO,
    COMMANDS_INFO,
    DATA_ATTACHMENTS,
    INPUTS,
    OUTPUTS,
    COMMANDS,
    REFERENCES
}
