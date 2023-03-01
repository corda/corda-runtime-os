package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies the encumbrance of a [TransactionState]
 * <p>
 * The encumbrance is identified by a tag. The encumbrance group has the tag and the size of the encumbrance group,
 * i.e. the number of states encumbered with the same tag in the same transaction. This allows to easily check
 * that all states of one encumbrance group are present.
 */
@DoNotImplement
@CordaSerializable
public interface EncumbranceGroup {

    /**
     * Gets the encumbrance tag.
     *
     * @return Returns the encumbrance tag.
     */
    @NotNull
    String getTag();

    /**
     * Gets the number of states encumbered with the tag of this group.
     *
     * @return Returns the number of states encumbered with the tag of this group.
     */
    int getSize();
}
