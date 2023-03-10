package net.corda.v5.ledger.utxo.query;

import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

/**
 * A factory that is used to instantiate a {@link VaultNamedQueryBuilder} that can be used to build a vault named query.
 */
@DoNotImplement
public interface VaultNamedQueryBuilderFactory {

    /**
     * Creates a {@link VaultNamedQueryBuilder} instance with the given name.
     * @param queryName Name of the query.
     *
     * @return A builder instance with the name set.
     */
    @NotNull VaultNamedQueryBuilder create(@NotNull String queryName);
}
