package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.CordaSerializable;

/**
 * Defines a marker interface which must be implemented by all {@link Contract} commands.
 */
@CordaSerializable
public interface Command {
}
