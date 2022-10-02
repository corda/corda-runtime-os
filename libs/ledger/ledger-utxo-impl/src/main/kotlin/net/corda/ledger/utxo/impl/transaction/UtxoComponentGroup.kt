package net.corda.ledger.utxo.impl.transaction

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
enum class UtxoComponentGroup {
    METADATA_GROUP,
    NOTARY_GROUP,
    OUTPUTS_INFO_GROUP,
    COMMANDS_INFO_GROUP,
    DATA_ATTACHMENTS_GROUP,
    INPUTS_GROUP,
    OUTPUTS_GROUP,
    COMMANDS_GROUP,
    REFERENCES_GROUP
}
