package net.corda.v5.ledger.utxo.query;

/**
 * An interface representing the base query builder which only consists of a register method.
 */
public interface VaultNamedQueryBuilderBase {
    /**
     * Registers the named query object to the named query registry.
     * This always needs to be called in order to "finalize" the query creation.
     */
    void register();
}
