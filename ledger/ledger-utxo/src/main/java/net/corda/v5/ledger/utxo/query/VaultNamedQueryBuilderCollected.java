package net.corda.v5.ledger.utxo.query;

/**
 * An interface representing a builder that has already been collected. After collecting, the only builder method that
 * can be called is {@link VaultNamedQueryBuilderBase#register()}.
 */
public interface VaultNamedQueryBuilderCollected extends VaultNamedQueryBuilderBase {
}
