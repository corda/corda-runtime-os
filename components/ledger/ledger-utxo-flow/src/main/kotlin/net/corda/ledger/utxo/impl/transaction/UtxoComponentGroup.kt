package net.corda.ledger.utxo.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
enum class UtxoComponentGroup {
    METADATA,
    NOTARY,
    OUTPUTS_INFO_ENCUMBRANCE,
    OUTPUTS_INFO_STATE_TYPE,
    OUTPUTS_INFO_CONTRACT_TYPE,
    COMMANDS_INFO,
    DATA_ATTACHMENTS,
    INPUTS,
    OUTPUTS,
    COMMANDS,
    REFERENCES
}
