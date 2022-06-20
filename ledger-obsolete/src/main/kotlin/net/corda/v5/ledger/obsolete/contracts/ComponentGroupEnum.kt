package net.corda.v5.ledger.obsolete.contracts

/**
 * An enum, for which each property corresponds to a transaction component group. The position in the enum class
 * declaration (ordinal) is used for component-leaf ordering when computing the Merkle tree.
 */
enum class ComponentGroupEnum {
    TRANSACTIONPARAMETERS_GROUP, // ordinal = 0.
    MEMBERSHIPPARAMETERS_GROUP, // ordinal = 1.
    PACKAGES_GROUP, // ordinal = 2.
    SIGNERS_GROUP, // ordinal = 3.
    INPUTSMETADATA_GROUP, // ordinal = 4.
    OUTPUTSDATA_GROUP, // ordinal = 5.
    COMMANDSMETADATA_GROUP, // ordinal = 6.
    REFERENCESMETADATA_GROUP, // ordinal = 7,
    ATTACHMENTS_GROUP, // ordinal = 8.
    NOTARY_GROUP, // ordinal = 9.
    TIMEWINDOW_GROUP, // ordinal = 10.
    INPUTS_GROUP, // ordinal = 11.
    OUTPUTS_GROUP, // ordinal = 12.
    COMMANDS_GROUP, // ordinal = 13.
    REFERENCES_GROUP, // ordinal = 14.
}
