package net.corda.v5.ledger.utxo.query;

import net.corda.v5.application.persistence.CordaPersistenceException;
import net.corda.v5.application.persistence.PagedQuery;
import net.corda.v5.application.persistence.ParameterizedQuery;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.ledger.utxo.StateAndRef;
import net.corda.v5.ledger.utxo.UtxoLedgerService;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Representation of a vault named query.
 * </p>
 * Ensure that {@link R} matches the type returned from the {@link VaultNamedQueryTransformer} and/or
 * {@link VaultNamedQueryCollector} (both optional) for the vault named query this object represents.
 * </p>
 * If no {@link VaultNamedQueryTransformer} or {@link VaultNamedQueryCollector} is applied to the vault named query,
 * the {@link ResultSet} from executing this query will contain {@link StateAndRef}s.
 *
 * @param <R> The type of results this query will fetch.
 *
 * @see UtxoLedgerService For thorough documentation of how to create and execute a vault named query.
 */
public interface VaultNamedParameterizedQuery<R> extends ParameterizedQuery<R> {

    /**
     * @see ParameterizedQuery#setLimit(int)
     */
    @Override
    @NotNull
    VaultNamedParameterizedQuery<R> setLimit(int limit);

    /**
     * @see ParameterizedQuery#setOffset(int)
     */
    @Override
    @NotNull
    VaultNamedParameterizedQuery<R> setOffset(int offset);

    /**
     * @see ParameterizedQuery#setParameter(String, Object)
     */
    @Override
    @NotNull
    VaultNamedParameterizedQuery<R> setParameter(@NotNull String name, @NotNull Object value);

    /**
     * @see ParameterizedQuery#setParameters(Map)
     */
    @Override
    @NotNull
    VaultNamedParameterizedQuery<R> setParameters(@NotNull Map<String, Object> parameters);

    /**
     * Sets the timestamp limit for the query. This will influence which results are returned.
     * @param timestampLimit The timestamp limit the query should enforce.
     * @return A {@link VaultNamedParameterizedQuery} object with the timestamp limit set.
     */
    @NotNull
    VaultNamedParameterizedQuery<R> setCreatedTimestampLimit(@NotNull Instant timestampLimit);

    /**
     * Executes the {@link VaultNamedParameterizedQuery} and creates a {@link PagedQuery.ResultSet} contains the results
     * of the query.
     * <p>
     * The results of the query depends on the executed named query and the {@link VaultNamedQueryTransformer} and/or
     * {@link VaultNamedQueryCollector} (both optional) applied to it.
     * </p>
     * If no {@link VaultNamedQueryTransformer} or {@link VaultNamedQueryCollector} is applied to the vault named query,
     * the {@link ResultSet} will contain {@link StateAndRef}s.
     *
     * @return A {@link ResultSet} containing the results of executing the query.
     *
     * @throws CordaPersistenceException If there is an error executing the query.
     */
    @Suspendable
    @Override
    @NotNull
    ResultSet<R> execute();
}
