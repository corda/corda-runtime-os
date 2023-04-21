package net.corda.v5.ledger.utxo.query;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Representation of a transformer function that will be applied to the result set returned by the named query.
 * Null values returned from the transformation will be filtered out.
 * <p>
 * Example usage:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * class MyVaultNamedQueryTransformer : VaultNamedQueryTransformer<ContractState, MyPojo> {
 *
 *     override fun transform(state: ContractState, parameters: Map<String, Any>): MyPojo {
 *         return MyPojo(1)
 *     }
 * }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class MyVaultNamedQueryTransformer implements VaultNamedQueryTransformer<ContractState, MyPojo> {
 *
 *     @Override
 *     public MyPojo transform(ContractState state, Map<String, Object> parameters) {
 *         return new MyPojo(1);
 *     }
 * }
 * }</pre></li></ul>
 *
 * @param <T> Type of the data returned from the database.
 * @param <R> Type that the original data is transformed into.
 */
public interface VaultNamedQueryTransformer<T, R> {
    @NotNull
    R transform(@NotNull T data, @NotNull Map<String, Object> parameters);
}
