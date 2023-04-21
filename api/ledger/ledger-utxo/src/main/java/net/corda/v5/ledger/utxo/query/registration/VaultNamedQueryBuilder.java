package net.corda.v5.ledger.utxo.query.registration;

import net.corda.v5.ledger.utxo.query.VaultNamedQueryCollector;
import net.corda.v5.ledger.utxo.query.VaultNamedQueryFilter;
import net.corda.v5.ledger.utxo.query.VaultNamedQueryTransformer;
import org.jetbrains.annotations.NotNull;

/**
 * A builder that is used to create and build a vault named query.
 * <p>
 * The register method is called when the build is finished so that the query is stored and can be fetched and executed
 * later on.
 */
public interface VaultNamedQueryBuilder extends VaultNamedQueryBuilderBase {
    /**
     * Sets the where clause of the named query.
     * @param query The JSON query representation.
     *
     * @return A builder instance with the where clause set.
     */
    @NotNull
    VaultNamedQueryBuilder whereJson(@NotNull String query);

    /**
     * Sets the filter function of the named query.
     * Note that filtering will always be applied before mapping.
     *
     * @param filter A filter object.
     *
     * @return A builder instance with the filter function set.
     */
    @NotNull VaultNamedQueryBuilder filter(@NotNull VaultNamedQueryFilter<?> filter);

    /**
     * Sets the mapper function of the named query.
     * Note that the transformation will always be applied after filtering.
     *
     * @param transformer A transformer object.
     *
     * @return A builder instance with the mapper function set.
     */
    @NotNull VaultNamedQueryBuilder map(@NotNull VaultNamedQueryTransformer<?, ?> transformer);

    /**
     * Sets the collector function of the named query.
     * @param collector A collector object.
     *
     * @return A builder instance with the collector function set.
     */
    @NotNull
    VaultNamedQueryBuilderCollected collect(@NotNull VaultNamedQueryCollector<?, ?> collector);
}
