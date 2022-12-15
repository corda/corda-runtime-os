package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement

/**
 * Identifies the encumbrance of a [TransactionState]
 *
 * The encumbrance is identified by a tag. The encumbrance group has the tag and the size of the encumbrance group,
 * i.e. the number of states encumbered with the same tag in the same transaction. This allows to easily check
 * that all states of one encumbrance group are present.
 *
 * @property tag The encumbrance tag
 * @property size The number of states encumbered with the tag of this group
 */
@DoNotImplement
@CordaSerializable
interface EncumbranceGroup {
    val tag: String
    val size: Int
}