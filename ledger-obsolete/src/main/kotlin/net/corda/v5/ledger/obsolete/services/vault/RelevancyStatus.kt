package net.corda.v5.ledger.obsolete.services.vault

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.obsolete.services.vault.RelevancyStatus.NOT_RELEVANT
import net.corda.v5.ledger.obsolete.services.vault.RelevancyStatus.RELEVANT

/**
 * If the querying node is a participant in a state then it is classed as [RELEVANT].
 *
 * If the querying node is not a participant in a state then it is classed as [NOT_RELEVANT]. These types of
 * states can still be recorded in the vault if the transaction containing them was recorded with the
 * [net.corda.v5.ledger.obsolete.services.StatesToRecord.ALL_VISIBLE] flag. This will typically happen for things like reference data which can be
 * referenced in transactions as a [net.corda.v5.ledger.obsolete.contracts.ReferencedStateAndRef] but cannot be modified by any party but the maintainer.
 */
@CordaSerializable
enum class RelevancyStatus {
    RELEVANT, NOT_RELEVANT
}